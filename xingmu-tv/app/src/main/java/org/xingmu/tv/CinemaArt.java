package org.xingmu.tv;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Original vector artwork drawn locally, no poster downloads or tracking. */
final class CinemaArt extends View {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int kind,color;private final boolean hero;
 CinemaArt(Context context,int kind,int color,boolean hero){super(context);this.kind=kind;this.color=color;this.hero=hero;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
  paint.setShader(new LinearGradient(0,h,w,0,new int[]{0xff101d34,color&0x00ffffff|0xff000000},null,Shader.TileMode.CLAMP));paint.setAlpha(hero?90:70);canvas.drawRect(0,0,w,h,paint);paint.setShader(null);paint.setAlpha(255);
  canvas.save();float unit=hero?h*.72f:Math.min(w,h)*.68f;canvas.translate(hero?w*.80f:w*.50f,h*.50f);canvas.scale(unit/200,unit/200);
  paint.setColor(color);paint.setAlpha(30);canvas.drawCircle(0,0,142,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setAlpha(65);canvas.drawCircle(0,0,120,paint);canvas.drawCircle(0,0,151,paint);paint.setAlpha(200);paint.setStrokeWidth(4);
  if(kind==0){canvas.drawRoundRect(-80,-52,80,48,12,12,paint);canvas.drawLine(-30,68,30,68,paint);canvas.drawLine(0,48,0,68,paint);canvas.drawLine(-25,-80,0,-56,paint);canvas.drawLine(25,-80,0,-56,paint);}
  else if(kind==2){canvas.drawCircle(0,0,65,paint);canvas.drawOval(-30,-65,30,65,paint);canvas.drawLine(-65,0,65,0,paint);canvas.drawOval(-60,-28,60,28,paint);}
  else if(kind==3){Path mask=new Path();mask.moveTo(-62,-63);mask.quadTo(0,-36,62,-63);mask.lineTo(55,21);mask.quadTo(0,105,-55,21);mask.close();canvas.drawPath(mask,paint);canvas.drawLine(-37,-8,-16,-2,paint);canvas.drawLine(16,-2,37,-8,paint);canvas.drawArc(-25,5,25,44,10,160,false,paint);}
  else{canvas.rotate(-9);canvas.drawRoundRect(-80,-58,80,62,13,13,paint);canvas.drawLine(-80,-24,80,-24,paint);for(int x=-60;x<70;x+=40)canvas.drawLine(x,-56,x+20,-26,paint);paint.setStyle(Paint.Style.FILL);Path play=new Path();play.moveTo(-12,-4);play.lineTo(25,18);play.lineTo(-12,40);play.close();canvas.drawPath(play,paint);}
  paint.setStyle(Paint.Style.FILL);paint.setAlpha(100);canvas.drawCircle(-113,-65,4,paint);canvas.drawCircle(102,80,5,paint);canvas.restore();paint.setAlpha(255);
  if(hero){paint.setShader(new LinearGradient(0,0,w*.67f,0,new int[]{0xff101b30,0xf0101b30,0x00101b30},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);paint.setShader(null);}
 }
}
