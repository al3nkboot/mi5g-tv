package com.gathin.tvhome;
import static org.junit.Assert.*;
import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class TransferServer21Test {
 @After public void stop(){ArrangeServer.stopRunning();}
 private String request(ArrangeServer server,String path,String body,long length)throws Exception{
  java.lang.reflect.Field field=ArrangeServer.class.getDeclaredField("socket");field.setAccessible(true);int port=((ServerSocket)field.get(server)).getLocalPort();
  try(Socket socket=new Socket("127.0.0.1",port)){
   socket.setSoTimeout(5000);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
   String headers=(body.isEmpty()&&length==0?"GET ":"POST ")+path+" HTTP/1.1\r\nHost: localhost\r\nContent-Length: "+length+"\r\n\r\n";
   socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(bytes);socket.getOutputStream().flush();
   return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
  }
 }
 @Test public void tokenGatesMessagesAndOversizeUploads()throws Exception{
  Context c=RuntimeEnvironment.getApplication();ArrangeServer server=ArrangeServer.start(c,null);
  java.lang.reflect.Field token=ArrangeServer.class.getDeclaredField("token");token.setAccessible(true);String prefix="/"+token.get(server)+"/";
  assertTrue(request(server,"/wrong/api/state","",0).startsWith("HTTP/1.1 404"));
  String body="{\"line1\":\"أهلًا بكم\",\"line2\":\"@midroid5G\"}";
  assertTrue(request(server,prefix+"api/message",body,body.getBytes(StandardCharsets.UTF_8).length).startsWith("HTTP/1.1 200"));
  assertEquals("أهلًا بكم",ScreensaverMessage.get(c)[0]);
  assertTrue(request(server,prefix+"api/state","",0).contains("@midroid5G"));
  assertTrue(request(server,prefix+"api/files/upload?name=test.apk","",FileTransfer.MAX_BYTES+1).startsWith("HTTP/1.1 400"));
  FileTransfer.outgoing=android.net.Uri.parse("content://test/file");server.stop();assertNull(FileTransfer.outgoing);
 }
 @Test @Config(sdk=28) public void uploadsStayFilesAndPartialUploadRollsBack()throws Exception{
  android.app.Application c=RuntimeEnvironment.getApplication();Shadows.shadowOf(c).grantPermissions(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
  byte[] bytes="APK is only a file".getBytes(StandardCharsets.UTF_8);
  org.json.JSONObject record=FileTransfer.receive(c,"../example.apk",new ByteArrayInputStream(bytes),bytes.length);
  assertEquals(bytes.length,record.getLong("size"));assertEquals(1,FileTransfer.listing(c).length());
  ByteArrayOutputStream download=new ByteArrayOutputStream();FileTransfer.download(c,record.getString("id"),download);assertTrue(download.toString("UTF-8").endsWith("APK is only a file"));
  try{FileTransfer.receive(c,"partial",new ByteArrayInputStream(new byte[1]),2);fail();}catch(IOException expected){}
  assertEquals(1,FileTransfer.listing(c).length());
 }
}
