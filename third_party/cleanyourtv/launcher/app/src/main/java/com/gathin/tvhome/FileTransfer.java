package com.gathin.tvhome;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Bounded, token-gated transfers only. Received APKs are files, never executed or installed. */
final class FileTransfer {
 static final long MAX_BYTES=64L*1024*1024, QUOTA_BYTES=512L*1024*1024;
 private static final String RECORDS="mi5g_received_files";
 static volatile Uri outgoing;
 static String cleanName(String raw){
  StringBuilder cleaned=new StringBuilder();
  if(raw!=null)for(int i=0;i<raw.length()&&cleaned.length()<100;i++){char ch=raw.charAt(i);int type=Character.getType(ch);cleaned.append(ch=='/'||ch=='\\'||type==Character.CONTROL||type==Character.FORMAT?'_':ch);}
  String n=cleaned.toString().trim();
  if(n.startsWith("."))n="file"+n;return n.isEmpty()?"file.bin":n.substring(0,Math.min(100,n.length()));
 }
 static JSONArray records(Context c){try{return new JSONArray(c.getSharedPreferences(Config.SETTINGS_NAME,0).getString(RECORDS,"[]"));}catch(Exception e){return new JSONArray();}}
 static synchronized JSONObject receive(Context c,String raw,InputStream in,long length)throws Exception{
  if(length<=0||length>MAX_BYTES)throw new IOException("size");
  JSONArray rows=records(c);long total=0;for(int i=0;i<rows.length();i++)total+=rows.getJSONObject(i).optLong("size");
  if(rows.length()>=100||total+length>QUOTA_BYTES)throw new IOException("quota");
  String name="Mi5G-"+System.currentTimeMillis()+"-"+cleanName(raw);Uri uri=null;File legacy=null;
  try{
   OutputStream out;
   if(Build.VERSION.SDK_INT>=29){ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,name);values.put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream");values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Mi5G");values.put(MediaStore.Downloads.IS_PENDING,1);uri=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("downloads");out=c.getContentResolver().openOutputStream(uri);}
   else{
    if(c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new IOException("storage permission");
    File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"Mi5G");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("downloads");legacy=File.createTempFile("Mi5G-","-"+cleanName(raw),dir);uri=Uri.fromFile(legacy);out=new FileOutputStream(legacy);
   }
   if(out==null)throw new IOException("output");
   try(OutputStream sink=out){byte[] buf=new byte[32768];long remaining=length;while(remaining>0){int n=in.read(buf,0,(int)Math.min(buf.length,remaining));if(n<0)throw new IOException("short upload");sink.write(buf,0,n);remaining-=n;}}
   if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);c.getContentResolver().update(uri,done,null,null);}
   JSONObject record=new JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("name",cleanName(raw)).put("size",length).put("uri",uri.toString());rows.put(record);
   c.getSharedPreferences(Config.SETTINGS_NAME,0).edit().putString(RECORDS,rows.toString()).commit();return record;
  }catch(Exception e){if(legacy!=null)legacy.delete();else if(uri!=null)c.getContentResolver().delete(uri,null,null);throw e;}
 }
 static JSONArray listing(Context c)throws Exception{JSONArray out=new JSONArray();JSONArray rows=records(c);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);out.put(new JSONObject().put("id",r.getString("id")).put("name",r.getString("name")).put("size",r.getLong("size")));}if(outgoing!=null)out.put(new JSONObject().put("id","selected").put("name",c.getString(R.string.mi5g_selected_file)).put("size",-1));return out;}
 static Uri find(Context c,String id)throws Exception{if("selected".equals(id))return outgoing;JSONArray rows=records(c);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(r.optString("id").equals(id))return Uri.parse(r.getString("uri"));}return null;}
 static void download(Context c,String id,OutputStream out)throws Exception{
  Uri uri=find(c,id);if(uri==null)throw new FileNotFoundException();
  // Stage at most MAX_BYTES so HTTP Content-Length is known and oversized selected files are refused.
  File tmp=File.createTempFile("transfer-",".tmp",c.getCacheDir());
  try{
   try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream file=new FileOutputStream(tmp)){
    if(in==null)throw new FileNotFoundException();byte[] b=new byte[32768];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("size");file.write(b,0,n);}
   }
   String headers="HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=Mi5G-download.bin\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\nContent-Length: "+tmp.length()+"\r\n\r\n";
   out.write(headers.getBytes(StandardCharsets.US_ASCII));try(InputStream file=new FileInputStream(tmp)){byte[] b=new byte[32768];int n;while((n=file.read(b))!=-1)out.write(b,0,n);}out.flush();
  }finally{tmp.delete();}
 }
}
