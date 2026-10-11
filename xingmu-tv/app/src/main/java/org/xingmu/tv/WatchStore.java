package org.xingmu.tv;
import android.content.Context;import android.content.SharedPreferences;import org.json.JSONArray;import java.util.*;
/** Local-only history, favourites and resumable positions, bounded to 50 history entries. */
final class WatchStore {
 private final SharedPreferences prefs;
 WatchStore(Context context){prefs=context.getSharedPreferences("watch",0);if(!prefs.getBoolean("migrated",false)){SharedPreferences.Editor edit=prefs.edit().putBoolean("migrated",true);for(Map.Entry<String,?> entry:context.getSharedPreferences("PlayerActivity",0).getAll().entrySet())if(entry.getKey().startsWith("position_")&&entry.getValue() instanceof Long)edit.putLong("pos_"+entry.getKey().substring(9),(Long)entry.getValue());edit.apply();}}
 boolean favourite(String id){return prefs.getBoolean("fav_"+id,false);}
 void toggle(String id){prefs.edit().putBoolean("fav_"+id,!favourite(id)).apply();}
 List<String> history(){List<String> ids=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("history","[]"));for(int i=0;i<a.length();i++)ids.add(a.getString(i));}catch(Exception ignored){}return ids;}
 void watched(String id){List<String> ids=history();ids.remove(id);ids.add(0,id);if(ids.size()>50)ids=ids.subList(0,50);prefs.edit().putString("history",new JSONArray(ids).toString()).apply();}
 long position(String id){return prefs.getLong("pos_"+id,0);}
 void position(String id,long value){prefs.edit().putLong("pos_"+id,Math.max(0,value)).apply();}
 void clearHistory(){SharedPreferences.Editor edit=prefs.edit().remove("history");for(String key:prefs.getAll().keySet())if(key.startsWith("pos_"))edit.remove(key);edit.apply();}
}
