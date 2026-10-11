package org.xingmu.tv;
import android.app.Activity;import android.view.*;import android.widget.*;import androidx.media3.exoplayer.ExoPlayer;
/** Intercept horizontal swipes in the video area; leave the native time bar and taps intact. */
final class SeekGestureFrame extends FrameLayout {
 interface PlayerProvider{ExoPlayer get();}interface Enabled{boolean get();}
 private final PlayerProvider provider;private final Enabled enabled;private final TextView feedback;private final float threshold;private float downX,downY;private long start,target;private boolean dragging,eligible;private final Runnable hide=this::hideFeedback;
 private void hideFeedback(){feedback.setVisibility(View.GONE);}
 SeekGestureFrame(Activity activity,PlayerProvider provider,Enabled enabled){super(activity);this.provider=provider;this.enabled=enabled;threshold=TvUi.dp(activity,18);feedback=TvUi.title(activity,"",20);feedback.setPadding(24,16,24,16);feedback.setGravity(Gravity.CENTER);feedback.setBackground(TvUi.box(0xdd101217,0));feedback.setVisibility(View.GONE);}
 private boolean available(){ExoPlayer p=provider.get();return enabled.get()&&p!=null&&p.getDuration()>0;}
 @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){dragging=false;downX=event.getX();downY=event.getY();eligible=available()&&downY>getHeight()*.16f&&downY<getHeight()*.72f;ExoPlayer p=provider.get();start=p==null?0:p.getCurrentPosition();}else if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&eligible){float dx=event.getX()-downX,dy=event.getY()-downY;if(Math.abs(dx)>threshold&&Math.abs(dx)>Math.abs(dy)*1.5f){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);preview(event.getX());return true;}if(Math.abs(dy)>threshold)eligible=false;}return false;}
 @Override public boolean onTouchEvent(MotionEvent event){if(!dragging)return super.onTouchEvent(event);if(event.getActionMasked()==MotionEvent.ACTION_MOVE)preview(event.getX());else if(event.getActionMasked()==MotionEvent.ACTION_UP){preview(event.getX());ExoPlayer p=provider.get();if(p!=null&&available())p.seekTo(target);dragging=false;removeCallbacks(hide);postDelayed(hide,1000);}else if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;feedback.setVisibility(View.GONE);}return true;}
 private void preview(float x){ExoPlayer p=provider.get();if(p==null)return;target=SeekMath.target(start,x-downX,getWidth(),p.getDuration());show("跳转至 "+SeekMath.time(target)+"\n松手确认");}
 void show(String text){if(feedback.getParent()==null)addView(feedback,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));feedback.bringToFront();feedback.setText(text);feedback.setVisibility(View.VISIBLE);removeCallbacks(hide);postDelayed(hide,1500);}
 @Override protected void onDetachedFromWindow(){removeCallbacks(hide);super.onDetachedFromWindow();}
}
