package com.gathin.tvhome;
import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.tvprovider.media.tv.TvContractCompat;
import java.util.*;
/** Local TV provider data only; querying and displaying it is opt-in. */
final class ContinueWatching {
 static final String KEY="mi5g_continue_watching";
 static boolean enabled(Context c){return c.getSharedPreferences(Config.SETTINGS_NAME,Context.MODE_PRIVATE).getBoolean(KEY,false);}
 static final class Item {
  final String title,pkg,uri;final long position,duration;
  Item(String t,String p,String u,long pos,long dur){title=t;pkg=p;uri=u;position=pos;duration=dur;}
  int progress(){return position>0&&duration>position?(int)Math.max(1,Math.min(99,position*100.0/duration)):-1;}
 }
 static List<Item> dedupe(List<Item> items){
  LinkedHashMap<String,Item> unique=new LinkedHashMap<>();
  for(Item i:items){String title=java.text.Normalizer.normalize(i.title,java.text.Normalizer.Form.NFKC).trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);if(!title.isEmpty()&&!unique.containsKey(title))unique.put(title,i);}
  return new ArrayList<>(unique.values());
 }
 static List<Item> read(Context c){
  List<Item> items=new ArrayList<>();
  for(Uri source:new Uri[]{TvContractCompat.WatchNextPrograms.CONTENT_URI,TvContractCompat.PreviewPrograms.CONTENT_URI}){
   try(Cursor cursor=c.getContentResolver().query(source,null,null,null,null)){
    int n=0;while(cursor!=null&&n++<100&&cursor.moveToNext()){
     String title=str(cursor,"title"),pkg=str(cursor,"package_name"),uri=str(cursor,"intent_uri");
     if(!HomeLayout.validPackage(pkg)||title.isEmpty()||uri.isEmpty())continue;
     long pos=num(cursor,"last_playback_position_millis"),dur=num(cursor,"duration_millis");
     if(source.equals(TvContractCompat.PreviewPrograms.CONTENT_URI) && (pos<=0 || (dur>0&&pos>=dur)))continue;
     items.add(new Item(title.substring(0,Math.min(512,title.length())),pkg,uri,pos,dur));
    }
   }catch(RuntimeException ignored){} // Provider may not exist or may refuse access on vendor firmware.
  }
  List<Item> out=dedupe(items);return out.subList(0,Math.min(12,out.size()));
 }
 private static String str(Cursor c,String key){int i=c.getColumnIndex(key);return i<0||c.isNull(i)?"":c.getString(i);}
 private static long num(Cursor c,String key){int i=c.getColumnIndex(key);return i<0||c.isNull(i)?0:c.getLong(i);}
 static void refresh(Activity a,LinearLayout container){
  container.removeAllViews();container.setVisibility(View.GONE);if(!enabled(a))return;
  new Thread(()->{List<Item> items=read(a);a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed()&&enabled(a))bind(a,container,items);});},"Mi5gWatchNext").start();
 }
 static void bind(Activity a,LinearLayout container,List<Item> items){
  container.removeAllViews();items=dedupe(items);container.setVisibility(items.isEmpty()?View.GONE:View.VISIBLE);if(items.isEmpty())return;
  TextView label=new TextView(a);label.setText(R.string.mi5g_continue_watching);label.setTypeface(a.getResources().getFont(R.font.cairo));label.setTextColor(0xFFD4AF37);label.setTextSize(17);container.addView(label);
  HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(a);row.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);scroll.addView(row);container.addView(scroll);
  float d=a.getResources().getDisplayMetrics().density;
  for(Item item:items){
   LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setPadding((int)(12*d),(int)(6*d),(int)(12*d),(int)(6*d));card.setBackgroundResource(R.drawable.glass_panel);card.setFocusable(true);card.setClickable(true);
   TextView title=new TextView(a);title.setText(item.title);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setTextSize(18);title.setTypeface(a.getResources().getFont(R.font.cairo));title.setTextColor(0xFFFFFFFF);card.addView(title);
   if(item.progress()>=0){ProgressBar progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgress(item.progress());card.addView(progress,new LinearLayout.LayoutParams(-1,(int)(8*d)));}
   card.setOnFocusChangeListener((v,f)->v.setBackgroundColor(f?0xFF0E6F66:0xFF12302E));
   card.setOnClickListener(v->open(a,item));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams((int)(220*d),(int)(64*d));lp.setMargins((int)(4*d),0,(int)(4*d),0);row.addView(card,lp);
  }
 }
 private static void open(Context c,Item item){
  try{
   Intent parsed=Intent.parseUri(item.uri,Intent.URI_INTENT_SCHEME);Uri data=parsed.getData();if(data==null||data.getScheme()==null)return;
   String scheme=data.getScheme().toLowerCase(Locale.ROOT);if(Arrays.asList("file","javascript","data","intent").contains(scheme))return;
   Intent view=new Intent(Intent.ACTION_VIEW,data).setPackage(item.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   if(view.resolveActivity(c.getPackageManager())!=null)c.startActivity(view);
  }catch(Exception ignored){}
 }
}
