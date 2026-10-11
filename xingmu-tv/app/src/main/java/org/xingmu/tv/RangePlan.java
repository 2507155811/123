package org.xingmu.tv;
import java.util.regex.*;
/** Byte ranges must cover the file exactly, without overlap. */
final class RangePlan {
 static long[] responseRange(String header){if(header==null)return null;Matcher m=Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matcher(header.trim());if(!m.matches())return null;try{long start=Long.parseLong(m.group(1)),end=Long.parseLong(m.group(2)),total=Long.parseLong(m.group(3));return start>=0&&end>=start&&end<total?new long[]{start,end,total}:null;}catch(NumberFormatException e){return null;}}
 static boolean matchesResponse(String header,long position,long length,String expectedValidator,String receivedValidator){long[] range=responseRange(header);return range!=null&&range[0]==position&&range[2]==length&&expectedValidator!=null&&expectedValidator.equals(receivedValidator);}
 static long[][] split(long length,int connections){if(length<=0||connections<1)throw new IllegalArgumentException();int count=(int)Math.min(length,connections);long[][] ranges=new long[count][2];long position=0,part=length/count,extra=length%count;for(int n=0;n<count;n++){long size=part+(n<extra?1:0);ranges[n]=new long[]{position,size};position+=size;}return ranges;}
}
