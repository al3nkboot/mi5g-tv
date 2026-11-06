package com.gathin.tvhome;
import java.util.*;
final class MemoryStats {
 static long availableMb(String text){
  Map<String,Long> m=new HashMap<>();
  for(String line:text.split("\n")){String[] p=line.trim().split("\\s+");if(p.length>=2)try{m.put(p[0],Math.max(0,Long.parseLong(p[1])));}catch(NumberFormatException ignored){}}
  Long a=m.get("MemAvailable:");
  if(a==null){if(!m.containsKey("MemFree:"))return -1; a=m.get("MemFree:")+m.getOrDefault("Buffers:",0L)+m.getOrDefault("Cached:",0L)+m.getOrDefault("SReclaimable:",0L)-m.getOrDefault("Shmem:",0L);}
  return Math.max(0,Math.min(a,m.getOrDefault("MemTotal:",Long.MAX_VALUE)))/1024;
 }
}
