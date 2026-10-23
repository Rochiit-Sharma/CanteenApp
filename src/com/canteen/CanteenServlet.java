package com.canteen;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@WebServlet(name="CanteenServlet", urlPatterns="/api/*", loadOnStartup=1)
public class CanteenServlet extends HttpServlet {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Set<String> STATUSES = Set.of("RECEIVED","PREPARING","READY","COMPLETED","CANCELLED");

  @Override public void init() throws ServletException {
    try { Database.init(); getServletContext().log("CanteenApp database is ready."); }
    catch (SQLException e) { getServletContext().log("Database startup failed. Check canteen_app, DB_USER/DB_PASSWORD and Connector/J.", e); throw new ServletException("CanteenApp could not connect to MySQL.", e); }
  }

  @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    headers(resp); String path = path(req);
    try {
      if ("/me".equals(path)) {
        Map<String,Object> user = user(req);
        if (user == null) { send(resp,401,Json.obj("error","Please sign in.")); return; }
        send(resp,200,Json.obj("user",user,"csrfToken",req.getSession(false).getAttribute("csrf"))); return;
      }
      Map<String,Object> user = requiredUser(req,resp); if (user == null) return;
      switch (path) {
        case "/menu": send(resp,200,Json.obj("items",menu(false))); break;
        case "/cart": send(resp,200,Json.obj("items",cart(id(user)))); break;
        case "/orders":
          if (role(user,"STUDENT",resp)) send(resp,200,Json.obj("orders",orders(id(user),false)));
          break;
        case "/admin/menu":
          if (role(user,"ADMIN",resp)) send(resp,200,Json.obj("items",menu(true)));
          break;
        case "/admin/orders":
          if (role(user,"ADMIN",resp)) send(resp,200,Json.obj("orders",orders(0,true)));
          break;
        case "/admin/stats":
          if (role(user,"ADMIN",resp)) send(resp,200,stats());
          break;
        default: send(resp,404,Json.obj("error","API route not found."));
      }
    } catch (ApiError e) { send(resp,e.status,Json.obj("error",e.getMessage())); }
      catch (Exception e) { getServletContext().log("GET " + path + " failed",e); send(resp,500,Json.obj("error","Something went wrong. Check the Tomcat log.")); }
  }

  @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    headers(resp); String path = path(req);
    try {
      if ("/login".equals(path)) { login(req,resp); return; }
      Map<String,Object> user = requiredUser(req,resp); if (user == null) return;
      if (!csrf(req,resp)) return;
      long uid = id(user);
      switch (path) {
        case "/logout":
          HttpSession session=req.getSession(false); if(session!=null) session.invalidate();
          send(resp,200,Json.obj("ok",true)); break;
        case "/cart/add": addToCart(req,resp,uid); break;
        case "/cart/update": updateCart(req,resp,uid); break;
        case "/cart/remove": removeCart(req,resp,uid); break;
        case "/orders":
          if (role(user,"STUDENT",resp)) send(resp,201,Json.obj("order",checkout(uid)));
          break;
        case "/admin/menu":
          if (role(user,"ADMIN",resp)) addMenu(req,resp);
          break;
        case "/admin/menu/toggle":
          if (role(user,"ADMIN",resp)) toggleMenu(req,resp);
          break;
        case "/admin/orders/status":
          if (role(user,"ADMIN",resp)) setOrderStatus(req,resp);
          break;
        default: send(resp,404,Json.obj("error","API route not found."));
      }
    } catch (ApiError e) { send(resp,e.status,Json.obj("error",e.getMessage())); }
      catch (Exception e) { getServletContext().log("POST " + path + " failed",e); send(resp,500,Json.obj("error","Something went wrong. Check the Tomcat log.")); }
  }

  private void login(HttpServletRequest req,HttpServletResponse resp) throws Exception {
    String username=param(req,"username",80).trim(), password=param(req,"password",200);
    if(username.isEmpty()||password.isEmpty()) throw new ApiError(400,"Enter your username and password.");
    Map<String,Object> found=null; String hash=null;
    try(Connection c=Database.connect();PreparedStatement ps=c.prepareStatement("SELECT id,username,full_name,password_hash,role FROM users WHERE username=?")){
      ps.setString(1,username);try(ResultSet rs=ps.executeQuery()){if(rs.next()){
        found=Json.obj("id",rs.getLong("id"),"username",rs.getString("username"),"fullName",rs.getString("full_name"),"role",rs.getString("role"));hash=rs.getString("password_hash");
      }}
    }
    if(found==null||hash==null||!Passwords.verify(password,hash)) throw new ApiError(401,"Incorrect username or password.");
    HttpSession old=req.getSession(false);if(old!=null)old.invalidate();
    HttpSession session=req.getSession(true);req.changeSessionId();session.setMaxInactiveInterval(1800);
    session.setAttribute("user",found);String token=token(32);session.setAttribute("csrf",token);
    send(resp,200,Json.obj("user",found,"csrfToken",token));
  }

  private List<Map<String,Object>> menu(boolean hidden) throws SQLException {
    List<Map<String,Object>> out=new ArrayList<>();
    String sql="SELECT id,name,description,category,price,available FROM menu_items "+(hidden?"":"WHERE available=TRUE ")+"ORDER BY category,name";
    try(Connection c=Database.connect();PreparedStatement ps=c.prepareStatement(sql);ResultSet rs=ps.executeQuery()){
      while(rs.next())out.add(Json.obj("id",rs.getLong("id"),"name",rs.getString("name"),"description",rs.getString("description"),"category",rs.getString("category"),"price",rs.getBigDecimal("price"),"available",rs.getBoolean("available")));
    } return out;
  }

  private List<Map<String,Object>> cart(long uid) throws SQLException {
    List<Map<String,Object>> out=new ArrayList<>();
    String sql="SELECT m.id,m.name,m.description,m.category,m.price,m.available,c.quantity FROM cart_items c JOIN menu_items m ON m.id=c.menu_item_id WHERE c.user_id=? ORDER BY m.name";
    try(Connection c=Database.connect();PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,uid);try(ResultSet rs=ps.executeQuery()){
      while(rs.next()){BigDecimal p=rs.getBigDecimal("price");int q=rs.getInt("quantity");
        out.add(Json.obj("id",rs.getLong("id"),"name",rs.getString("name"),"description",rs.getString("description"),"category",rs.getString("category"),"price",p,"quantity",q,"available",rs.getBoolean("available"),"lineTotal",p.multiply(BigDecimal.valueOf(q))));
      }
    }}return out;
  }

  private List<Map<String,Object>> orders(long uid,boolean all) throws SQLException {
    List<Map<String,Object>> out=new ArrayList<>();
    String sql="SELECT o.id,o.user_id,u.username,o.ticket_code,o.total,o.status,o.created_at FROM orders o JOIN users u ON u.id=o.user_id "+(all?"":"WHERE o.user_id=? ")+"ORDER BY o.created_at DESC,o.id DESC";
    try(Connection c=Database.connect();PreparedStatement ps=c.prepareStatement(sql)){
      if(!all)ps.setLong(1,uid);
      try(ResultSet rs=ps.executeQuery()){while(rs.next()){
        long oid=rs.getLong("id");List<Map<String,Object>> items=new ArrayList<>();
        try(PreparedStatement ip=c.prepareStatement("SELECT item_name,unit_price,quantity FROM order_items WHERE order_id=? ORDER BY id")){ip.setLong(1,oid);try(ResultSet ir=ip.executeQuery()){
          while(ir.next())items.add(Json.obj("name",ir.getString("item_name"),"price",ir.getBigDecimal("unit_price"),"quantity",ir.getInt("quantity")));
        }}
        out.add(Json.obj("id",oid,"userId",rs.getLong("user_id"),"username",rs.getString("username"),"ticketCode",rs.getString("ticket_code"),"total",rs.getBigDecimal("total"),"status",rs.getString("status"),"createdAt",rs.getTimestamp("created_at").toLocalDateTime().toString(),"items",items));
      }}
    }return out;
  }

  private Map<String,Object> stats() throws SQLException {
    try(Connection c=Database.connect();Statement s=c.createStatement()){
      return Json.obj("totalOrders",scalar(s,"SELECT COUNT(*) FROM orders"),"inProgress",scalar(s,"SELECT COUNT(*) FROM orders WHERE status IN ('RECEIVED','PREPARING')"),"menuItems",scalar(s,"SELECT COUNT(*) FROM menu_items"));
    }
  }
  private long scalar(Statement s,String sql)throws SQLException{try(ResultSet r=s.executeQuery(sql)){r.next();return r.getLong(1);}}

  private void addToCart(HttpServletRequest req,HttpServletResponse resp,long uid)throws Exception{
    long item=positive(req,"itemId");int q=integer(req,"quantity",1,20);
    try(Connection c=Database.connect();PreparedStatement check=c.prepareStatement("SELECT id FROM menu_items WHERE id=? AND available=TRUE")){
      check.setLong(1,item);try(ResultSet rs=check.executeQuery()){if(!rs.next())throw new ApiError(404,"That menu item is not available.");}
    }
    try(Connection c=Database.connect();PreparedStatement ps=c.prepareStatement("INSERT INTO cart_items(user_id,menu_item_id,quantity) VALUES(?,?,?) ON DUPLICATE KEY UPDATE quantity=LEAST(20,quantity+VALUES(quantity))")){
      ps.setLong(1,uid);ps.setLong(2,item);ps.setInt(3,q);ps.executeUpdate();
    }send(resp,200,Json.obj("ok",true));
  }
  private void updateCart(HttpServletRequest req,HttpServletResponse resp,long uid)throws Exception{
    long item=positive(req,"itemId");int q=integer(req,"quantity",0,20);
    try(Connection c=Database.connect()){
      if(q==0){try(PreparedStatement p=c.prepareStatement("DELETE FROM cart_items WHERE user_id=? AND menu_item_id=?")){p.setLong(1,uid);p.setLong(2,item);p.executeUpdate();}}
      else{try(PreparedStatement p=c.prepareStatement("UPDATE cart_items SET quantity=? WHERE user_id=? AND menu_item_id=?")){p.setInt(1,q);p.setLong(2,uid);p.setLong(3,item);if(p.executeUpdate()==0)throw new ApiError(404,"That item is not in your cart.");}}
    }send(resp,200,Json.obj("ok",true));
  }
  private void removeCart(HttpServletRequest req,HttpServletResponse resp,long uid)throws Exception{
    long item=positive(req,"itemId");try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("DELETE FROM cart_items WHERE user_id=? AND menu_item_id=?")){p.setLong(1,uid);p.setLong(2,item);p.executeUpdate();}send(resp,200,Json.obj("ok",true));
  }

  private Map<String,Object> checkout(long uid)throws Exception{
    try(Connection c=Database.connect()){
      c.setAutoCommit(false);
      try{
        List<Map<String,Object>> items=new ArrayList<>();
        String sql="SELECT m.id,m.name,m.price,c.quantity FROM cart_items c JOIN menu_items m ON m.id=c.menu_item_id WHERE c.user_id=? AND m.available=TRUE FOR UPDATE";
        try(PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,uid);try(ResultSet r=p.executeQuery()){
          while(r.next())items.add(Json.obj("id",r.getLong("id"),"name",r.getString("name"),"price",r.getBigDecimal("price"),"quantity",r.getInt("quantity")));
        }}
        if(items.isEmpty())throw new ApiError(400,"Your cart is empty or its items are unavailable.");
        BigDecimal total=BigDecimal.ZERO;for(Map<String,Object> item:items)total=total.add(((BigDecimal)item.get("price")).multiply(BigDecimal.valueOf(((Number)item.get("quantity")).longValue())));
        String ticket="CA-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);long oid;
        try(PreparedStatement p=c.prepareStatement("INSERT INTO orders(user_id,ticket_code,total,status) VALUES(?,?,?,'RECEIVED')",Statement.RETURN_GENERATED_KEYS)){
          p.setLong(1,uid);p.setString(2,ticket);p.setBigDecimal(3,total);p.executeUpdate();try(ResultSet keys=p.getGeneratedKeys()){if(!keys.next())throw new SQLException("Order ID missing");oid=keys.getLong(1);}
        }
        try(PreparedStatement p=c.prepareStatement("INSERT INTO order_items(order_id,menu_item_id,item_name,unit_price,quantity) VALUES(?,?,?,?,?)")){
          for(Map<String,Object> item:items){p.setLong(1,oid);p.setLong(2,((Number)item.get("id")).longValue());p.setString(3,(String)item.get("name"));p.setBigDecimal(4,(BigDecimal)item.get("price"));p.setInt(5,((Number)item.get("quantity")).intValue());p.addBatch();}p.executeBatch();
        }
        try(PreparedStatement p=c.prepareStatement("DELETE FROM cart_items WHERE user_id=?")){p.setLong(1,uid);p.executeUpdate();}
        c.commit();return Json.obj("id",oid,"ticketCode",ticket,"total",total,"status","RECEIVED");
      }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
    }
  }

  private void addMenu(HttpServletRequest req,HttpServletResponse resp)throws Exception{
    String name=param(req,"name",100).trim(),desc=param(req,"description",500).trim(),cat=param(req,"category",60).trim(),raw=param(req,"price",30).trim();
    if(name.isEmpty())throw new ApiError(400,"Enter a menu item name.");BigDecimal price;
    try{price=new BigDecimal(raw);}catch(NumberFormatException e){throw new ApiError(400,"Enter a valid price.");}
    if(price.compareTo(new BigDecimal("0.50"))<0||price.compareTo(new BigDecimal("100000"))>0)throw new ApiError(400,"Price must be between ₹0.50 and ₹100,000.");
    if(cat.isEmpty())cat="Meals";
    try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO menu_items(name,description,category,price,available) VALUES(?,?,?,?,TRUE)")){
      p.setString(1,name);p.setString(2,desc);p.setString(3,cat);p.setBigDecimal(4,price);p.executeUpdate();
    }send(resp,201,Json.obj("ok",true));
  }
  private void toggleMenu(HttpServletRequest req,HttpServletResponse resp)throws Exception{
    long item=positive(req,"id");String available=param(req,"available",10);
    if(!"true".equalsIgnoreCase(available)&&!"false".equalsIgnoreCase(available))throw new ApiError(400,"Invalid availability.");
    try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("UPDATE menu_items SET available=? WHERE id=?")){
      p.setBoolean(1,Boolean.parseBoolean(available));p.setLong(2,item);if(p.executeUpdate()==0)throw new ApiError(404,"Menu item not found.");
    }send(resp,200,Json.obj("ok",true));
  }
  private void setOrderStatus(HttpServletRequest req,HttpServletResponse resp)throws Exception{
    long oid=positive(req,"orderId");String status=param(req,"status",20).toUpperCase(Locale.ROOT);
    if(!STATUSES.contains(status))throw new ApiError(400,"Invalid order status.");
    try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("UPDATE orders SET status=? WHERE id=?")){
      p.setString(1,status);p.setLong(2,oid);if(p.executeUpdate()==0)throw new ApiError(404,"Order not found.");
    }send(resp,200,Json.obj("ok",true));
  }

  @SuppressWarnings("unchecked") private Map<String,Object> user(HttpServletRequest req){HttpSession s=req.getSession(false);Object value=s==null?null:s.getAttribute("user");return value instanceof Map?(Map<String,Object>)value:null;}
  private Map<String,Object> requiredUser(HttpServletRequest req,HttpServletResponse resp)throws IOException{Map<String,Object> u=user(req);if(u==null){send(resp,401,Json.obj("error","Your session has expired. Please sign in again."));return null;}return u;}
  private boolean role(Map<String,Object> u,String role,HttpServletResponse resp)throws IOException{if(!role.equals(u.get("role"))){send(resp,403,Json.obj("error","You do not have permission to do that."));return false;}return true;}
  private boolean csrf(HttpServletRequest req,HttpServletResponse resp)throws IOException{
    HttpSession s=req.getSession(false);String a=s==null?null:(String)s.getAttribute("csrf"),b=req.getHeader("X-CSRF-Token");
    if(a==null||b==null||!MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8))){send(resp,403,Json.obj("error","Security token expired. Reload and try again."));return false;}return true;
  }
  private String path(HttpServletRequest r){return r.getPathInfo()==null?"/":r.getPathInfo();}
  private String param(HttpServletRequest r,String key,int max){String s=r.getParameter(key);if(s==null)return "";if(s.length()>max)throw new ApiError(400,"Field is too long: "+key);return s;}
  private long positive(HttpServletRequest r,String key){try{long n=Long.parseLong(param(r,key,24));if(n<=0)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new ApiError(400,"Invalid "+key+".");}}
  private int integer(HttpServletRequest r,String key,int min,int max){try{int n=Integer.parseInt(param(r,key,12));if(n<min||n>max)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new ApiError(400,"Invalid "+key+".");}}
  private long id(Map<String,Object> u){return ((Number)u.get("id")).longValue();}
  private String token(int bytes){byte[] b=new byte[bytes];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
  private void headers(HttpServletResponse r){r.setHeader("X-Content-Type-Options","nosniff");r.setHeader("X-Frame-Options","DENY");r.setHeader("Referrer-Policy","same-origin");r.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");r.setHeader("Cache-Control","no-store");}
  private void send(HttpServletResponse r,int status,Object value)throws IOException{r.setStatus(status);r.setCharacterEncoding("UTF-8");r.setContentType("application/json;charset=UTF-8");r.getWriter().write(Json.stringify(value));}
  private static final class ApiError extends RuntimeException{final int status;ApiError(int s,String m){super(m);status=s;}}

  private static final class Database {
    static Connection connect()throws SQLException{
      String url=System.getenv().getOrDefault("DB_URL","jdbc:mysql://localhost:3306/canteen_app?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata");
      String user=System.getenv().getOrDefault("DB_USER","root"),pw=System.getenv().getOrDefault("DB_PASSWORD","root");
      try{Class.forName("com.mysql.cj.jdbc.Driver");}catch(ClassNotFoundException e){throw new SQLException("Install MySQL Connector/J in WEB-INF/lib.",e);}
      return DriverManager.getConnection(url,user,pw);
    }
    static void init()throws SQLException{
      try(Connection c=connect();Statement s=c.createStatement()){
        s.executeUpdate("CREATE TABLE IF NOT EXISTS users(id BIGINT PRIMARY KEY AUTO_INCREMENT,username VARCHAR(80) NOT NULL UNIQUE,full_name VARCHAR(120) NOT NULL,password_hash VARCHAR(255) NOT NULL,role VARCHAR(16) NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,CONSTRAINT chk_user_role CHECK(role IN ('STUDENT','ADMIN'))) ENGINE=InnoDB");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS menu_items(id BIGINT PRIMARY KEY AUTO_INCREMENT,name VARCHAR(100) NOT NULL,description VARCHAR(500) NOT NULL DEFAULT '',category VARCHAR(60) NOT NULL DEFAULT 'Meals',price DECIMAL(10,2) NOT NULL,available BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS cart_items(user_id BIGINT NOT NULL,menu_item_id BIGINT NOT NULL,quantity INT NOT NULL DEFAULT 1,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,PRIMARY KEY(user_id,menu_item_id),CONSTRAINT fk_cart_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,CONSTRAINT fk_cart_menu FOREIGN KEY(menu_item_id) REFERENCES menu_items(id) ON DELETE CASCADE,CONSTRAINT chk_cart_quantity CHECK(quantity BETWEEN 1 AND 20)) ENGINE=InnoDB");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS orders(id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,ticket_code VARCHAR(24) NOT NULL UNIQUE,total DECIMAL(10,2) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,CONSTRAINT fk_order_user FOREIGN KEY(user_id) REFERENCES users(id),INDEX idx_orders_user_created(user_id,created_at),INDEX idx_orders_status(status)) ENGINE=InnoDB");
        s.executeUpdate("CREATE TABLE IF NOT EXISTS order_items(id BIGINT PRIMARY KEY AUTO_INCREMENT,order_id BIGINT NOT NULL,menu_item_id BIGINT NULL,item_name VARCHAR(100) NOT NULL,unit_price DECIMAL(10,2) NOT NULL,quantity INT NOT NULL,CONSTRAINT fk_item_order FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE CASCADE,CONSTRAINT fk_item_menu FOREIGN KEY(menu_item_id) REFERENCES menu_items(id) ON DELETE SET NULL) ENGINE=InnoDB");
      }
      seedUser("admin","admin","Canteen Administrator","ADMIN");seedUser("student","student","Demo Student","STUDENT");seedMenu();
    }
    static void seedUser(String name,String password,String fullName,String role)throws SQLException{
      try(Connection c=connect();PreparedStatement q=c.prepareStatement("SELECT id FROM users WHERE username=?")){q.setString(1,name);try(ResultSet r=q.executeQuery()){if(r.next())return;}}
      try(Connection c=connect();PreparedStatement p=c.prepareStatement("INSERT INTO users(username,full_name,password_hash,role) VALUES(?,?,?,?)")){
        p.setString(1,name);p.setString(2,fullName);p.setString(3,Passwords.hash(password));p.setString(4,role);p.executeUpdate();
      }
    }
    static void seedMenu()throws SQLException{
      try(Connection c=connect();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM menu_items")){r.next();if(r.getInt(1)>0)return;}
      String[][] items={{"Masala Dosa","Crispy dosa with potato masala and chutney","Breakfast","65.00"},{"Veg Sandwich","Grilled sandwich with fresh vegetables","Snacks","55.00"},{"Paneer Rice Bowl","Paneer, rice and house masala","Meals","110.00"},{"Samosa (2 pcs)","Golden pastry with spiced potato filling","Snacks","30.00"},{"Cold Coffee","Chilled coffee with milk","Drinks","50.00"},{"Chocolate Brownie","Soft chocolate brownie","Desserts","45.00"}};
      try(Connection c=connect();PreparedStatement p=c.prepareStatement("INSERT INTO menu_items(name,description,category,price,available) VALUES(?,?,?,?,TRUE)")){for(String[] i:items){p.setString(1,i[0]);p.setString(2,i[1]);p.setString(3,i[2]);p.setBigDecimal(4,new BigDecimal(i[3]));p.addBatch();}p.executeBatch();}
    }
  }

  private static final class Passwords {
    static String hash(String password){byte[] salt=new byte[16];RANDOM.nextBytes(salt);byte[] h=derive(password.toCharArray(),salt,210000);Base64.Encoder e=Base64.getUrlEncoder().withoutPadding();return "pbkdf2_sha256$210000$"+e.encodeToString(salt)+"$"+e.encodeToString(h);}
    static boolean verify(String password,String encoded){try{String[] p=encoded.split("\\$",-1);if(p.length!=4||!"pbkdf2_sha256".equals(p[0]))return false;int rounds=Integer.parseInt(p[1]);if(rounds<100000||rounds>2000000)return false;return MessageDigest.isEqual(Base64.getUrlDecoder().decode(p[3]),derive(password.toCharArray(),Base64.getUrlDecoder().decode(p[2]),rounds));}catch(RuntimeException e){return false;}}
    static byte[] derive(char[] password,byte[] salt,int rounds){PBEKeySpec spec=new PBEKeySpec(password,salt,rounds,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}catch(Exception e){throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable",e);}finally{spec.clearPassword();Arrays.fill(password,'\\0');}}
  }

  private static final class Json {
    static Map<String,Object> obj(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i+1<pairs.length;i+=2)m.put(String.valueOf(pairs[i]),pairs[i+1]);return m;}
    static String stringify(Object v){
      if(v==null)return "null";
      if(v instanceof String||v instanceof Character)return quote(String.valueOf(v));
      if(v instanceof Number||v instanceof Boolean)return String.valueOf(v);
      if(v instanceof Map<?,?>){StringJoiner j=new StringJoiner(",","{","}");for(Map.Entry<?,?> e:((Map<?,?>)v).entrySet())j.add(quote(String.valueOf(e.getKey()))+":"+stringify(e.getValue()));return j.toString();}
      if(v instanceof Iterable<?>){StringJoiner j=new StringJoiner(",","[","]");for(Object x:(Iterable<?>)v)j.add(stringify(x));return j.toString();}
      return quote(String.valueOf(v));
    }
    static String quote(String s) {
      StringBuilder b = new StringBuilder();
      b.append('"');
      for (int i=0;i<s.length();i++) {
        char c=s.charAt(i);
        switch(c) {
          case '"': b.append((char)92).append('"'); break;
          case 92: b.append((char)92).append((char)92); break;
          case 8: b.append((char)92).append('b'); break;
          case 12: b.append((char)92).append('f'); break;
          case 10: b.append((char)92).append('n'); break;
          case 13: b.append((char)92).append('r'); break;
          case 9: b.append((char)92).append('t'); break;
          default:
            if(c<32) b.append((char)92).append('u').append(String.format("%04x",(int)c));
            else b.append(c);
        }
      }
      return b.append('"').toString();
    }
  }
}
