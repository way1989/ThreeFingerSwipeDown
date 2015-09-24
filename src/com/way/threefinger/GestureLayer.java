package com.way.threefinger;

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.widget.FrameLayout;

public class GestureLayer extends FrameLayout {
	private static final String TAG = "GestureLayer";
	private boolean mTouchEnabled = true;
	private boolean hasDetected;
	// most points during the process of gesture
	private int mostPointsReached;
	private Set<Integer> mPointOnScreen;
	private SparseArray<PointF> mInitPosition;

	private enum MoveType {
		UP, DOWN, LEFT, RIGHT, NONE
	};

	public GestureLayer(Context context) {
		this(context, null);
	}

	public GestureLayer(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public GestureLayer(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		mPointOnScreen = new HashSet<Integer>();
		mInitPosition = new SparseArray<PointF>();
		hasDetected = false;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		handleGestureEvent(ev);
		return filterUnnecessaryEvent(ev);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		super.onTouchEvent(event);
		if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
			// don't handle ACTION_DONW to avoid duplicate down event
			handleGestureEvent(event);
		}
		return true;
		// 这里返回true是为了防止一个ACTION_DOWN从头到尾都没有人处理
	}

	private boolean filterUnnecessaryEvent(MotionEvent event) {
		int action = event.getAction() & MotionEvent.ACTION_MASK;

		// avoid move when two finger on screen
		if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() >= 2) {
			return true;
		}

		// is touch is not enabled, ignore this event
		if (!mTouchEnabled) {
			// BugID:5305301,by wenliang.dwl
			// this happens before delete dialog shown
			return true;
		}

		return false;
	}

	/**
	 * 在onInterceptTouchEvent和onTouchEvent之中处理事件
	 */
	private void handleGestureEvent(MotionEvent event) {
		// When the dialog is displayed
		if (!mTouchEnabled)
			return;
		int action = event.getAction() & MotionEvent.ACTION_MASK;
		int N = event.getPointerCount();

		whatHappend(event);

		// 如果已经执行了锁屏或者其他操作，但是多余的Touch事件还在忘里面传
		// 这时候就要把多余的清理掉，用hasBeenLocked判断。直到下一个ACTION_DOWN表明新的按下的过程开始了
		if (hasDetected) {
			if (action == MotionEvent.ACTION_DOWN) {
				hasDetected = false;
			} else {
				return;
			}
		}
		assert N == mPointOnScreen.size();

		switch (action) {
		case MotionEvent.ACTION_DOWN:
			clear();
			onPointDown(event);
			// onTouchDown(event);
			break;
		case MotionEvent.ACTION_POINTER_DOWN:
			onPointDown(event);
			// onMultiTouchDown(event);
			break;
		case MotionEvent.ACTION_MOVE:
			onPointUpdate(event);
			break;
		case MotionEvent.ACTION_POINTER_UP:
			onPointUp(event);
			break;
		case MotionEvent.ACTION_UP:
			onPointUp(event);
			clear();
			break;
		case MotionEvent.ACTION_CANCEL:
			clear();
			break;
		default:
			clear();
			break;
		}
	}

	/**
	 * 当一个点下落到屏幕的时候调用
	 * 
	 * @param event
	 *            传递过来的MotionEvent对象
	 */
	private void onPointDown(MotionEvent event) {
		int pointIndex = event.getActionIndex();
		int pointId = event.getPointerId(pointIndex);
		PointF p = new PointF(event.getX(pointIndex), event.getY(pointIndex));
		mPointOnScreen.add(pointId);
		mInitPosition.put(pointId, p);
		mostPointsReached++;
	}

	/**
	 * 当一个点离开屏幕的时候调用
	 * 
	 * @param event
	 *            传递过来的MotionEvent对象
	 */
	private void onPointUp(MotionEvent event) {
		if (!checkProcessedDown(event)) {
			return;
		}

		int pointId = event.getPointerId(event.getActionIndex());
		mPointOnScreen.remove(pointId);
		mInitPosition.remove(pointId);

	}

	/**
	 * 在ACTION_MOVE的时候进行一些更新的动作，比如检测点移动的距离
	 */
	private void onPointUpdate(MotionEvent event) {
		if (!checkProcessedDown(event)) {
			return;
		}

		int numPoints = event.getPointerCount();
		if (numPoints < 2) {
			// handleSingleFingerMove(event);
			return;
		}

		PointF[] pNow = new PointF[numPoints];
		PointF[] pInit = new PointF[numPoints];
		for (int i = 0; i < numPoints; i++) {
			int pointId = event.getPointerId(i);
			pNow[i] = new PointF(event.getX(i), event.getY(i));
			pInit[i] = mInitPosition.get(pointId);
		}

		checkTwoFinger(pInit, pNow);
		checkThreeFinger(pInit, pNow);
	}

	/**
	 * @param pInit
	 *            两个指头按下时候的坐标
	 * @param pNow
	 *            两个指头滑动时候的坐标
	 */
	private void checkTwoFinger(PointF[] pInit, PointF[] pNow) {
		if (pInit.length != 2)
			return;
		if (pInit.length != pNow.length)
			return;
		if (mostPointsReached > 2)
			return;

		// 两指捏合
		if (perimeter(pNow) < perimeter(pInit) && isTwoFingerClose(pInit, pNow)) {
			float y0 = pInit[0].y;
			float y1 = pInit[1].y;

			Log.d(TAG, "y0 : " + y0 + " y1 : " + y1);
			if (y0 != y1) {
				// do something
				Log.i("liweiping", "两指捏合...");
				hasDetected = true;
			}
		}

		// 两指放大
		if (perimeter(pNow) > perimeter(pInit) && isTwoFingerOpen(pInit, pNow)) {
			float y0 = pInit[0].y;
			float y1 = pInit[1].y;

			Log.d(TAG, "y0 : " + y0 + " y1 : " + y1);
			if (y0 != y1) {
				// do something
				Log.i("liweiping", "两指放大...");
				hasDetected = true;
			}
		}
	}

	/**
	 * @param pInit
	 *            三个（及以上）指头按下时候的坐标
	 * @param pNow
	 *            三个（及以上）指头滑动时候的坐标
	 */
	private void checkThreeFinger(PointF[] pInit, PointF[] pNow) {

		if (pInit.length < 3)
			return;
		if (pInit.length != pNow.length)
			return;

		// 三指或者以上向外锁屏
		if (perimeter(pNow) > perimeter(pInit) * 2) {
			// do something
			Log.i("liweiping", "三指或者以上向外滑动...");
			hasDetected = true;
		}

		boolean isSwipeDown = true;
		for (int i = 0; i < pInit.length; i++) {
			MoveType type = getMoveType(pInit[i], pNow[i]);
			float yDelta = pNow[i].y - pInit[i].y;
			if (type != MoveType.DOWN || yDelta < 30) {
				isSwipeDown = false;
			}
		}
		if (isSwipeDown) {
			Log.i("liweiping", "三指或者以上向下滑动...");
			hasDetected = true;
		}
	}

	/**
	 * 检测一个点的移动方向
	 */
	private MoveType getMoveType(PointF pInit, PointF pNow) {
		float yDelta = pNow.y - pInit.y;
		float xDelta = pNow.x - pInit.x;
		float yAbs = Math.abs(yDelta);
		float xAbs = Math.abs(xDelta);

		if (yDelta < 0 && xAbs < yAbs)
			return MoveType.UP;
		if (yDelta > 0 && xAbs < yAbs)
			return MoveType.DOWN;
		if (xDelta < 0 && yAbs < xAbs)
			return MoveType.LEFT;
		if (xDelta > 0 && yAbs < xAbs)
			return MoveType.RIGHT;

		return MoveType.NONE;
	}

	/**
	 * 判断两指是否推开
	 */
	private boolean isTwoFingerOpen(PointF[] pInit, PointF[] pNow) {
		if (pInit.length != 2)
			return false;
		if (pInit.length != 2)
			return false;

		MoveType[] types = new MoveType[2];
		types[0] = getMoveType(pInit[0], pNow[0]);
		types[1] = getMoveType(pInit[1], pNow[1]);

		if (pInit[0].y < pInit[1].y) {
			if (types[0] == MoveType.UP && types[1] == MoveType.DOWN)
				return true;
		} else {
			if (types[1] == MoveType.UP && types[0] == MoveType.DOWN)
				return true;
		}

		return false;
	}

	/**
	 * 判断两指是否捏合
	 */
	private boolean isTwoFingerClose(PointF[] pInit, PointF[] pNow) {
		if (pInit.length != 2)
			return false;
		if (pInit.length != 2)
			return false;

		MoveType[] types = new MoveType[2];
		types[0] = getMoveType(pInit[0], pNow[0]);
		types[1] = getMoveType(pInit[1], pNow[1]);

		if (pInit[0].y > pInit[1].y) {
			if (types[0] == MoveType.UP && types[1] == MoveType.DOWN)
				return true;
		} else {
			if (types[1] == MoveType.UP && types[0] == MoveType.DOWN)
				return true;
		}

		return false;
	}

	/**
	 * 求两点之间的距离
	 */
	private double distance(PointF p1, PointF p2) {
		return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
	}

	/**
	 * 求周长
	 */
	private double perimeter(PointF[] points) {
		double sum = 0;

		if (points.length < 2)
			return sum;

		sum += distance(points[points.length - 1], points[0]);
		for (int i = 1; i < points.length; i++) {
			sum += distance(points[i - 1], points[i]);
		}
		return sum;
	}

	private boolean checkProcessedDown(MotionEvent event) {
		final int N = event.getPointerCount();
		for (int i = 0; i < N; i++) {
			int id = event.getPointerId(i);
			if (mInitPosition.get(id) == null) {
				return false;
			}
		}

		return true;
	}

	/**
	 * 清空变量
	 */
	private void clear() {
		mPointOnScreen.clear();
		mInitPosition.clear();
		mostPointsReached = 0;
	}

	public void setTouchEnabled(boolean mTouchEnabled) {
		this.mTouchEnabled = mTouchEnabled;
	}

	public int getPointerCount() {
		if (mPointOnScreen != null) {
			return mPointOnScreen.size();
		}
		return 0;
	}

	/**
	 * 打印触摸事件的信息
	 * 
	 * @param event
	 *            传递过来的MotionEvent对象
	 */
	private void whatHappend(MotionEvent event) {
		int id = event.getPointerId(event.getActionIndex());
		String loginfo = "";
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			loginfo = "ACTION_DOWN";
			break;
		case MotionEvent.ACTION_POINTER_DOWN:
			loginfo = "ACTION_POINTER_DOWN";
			break;
		case MotionEvent.ACTION_MOVE:
			loginfo = "ACTION_MOVE";
			break;
		case MotionEvent.ACTION_UP:
			loginfo = "ACTION_UP";
			break;
		case MotionEvent.ACTION_POINTER_UP:
			loginfo = "ACTION_POINTER_UP";
			break;
		case MotionEvent.ACTION_CANCEL:
			loginfo = "ACTION_CANCEL";
		default:
			break;
		}
		Log.d(TAG, id + " " + loginfo + " point count = " + event.getPointerCount());
	}
}
