/*
 * Copyright (C) 2013 Andreas Stuetz <andreas.stuetz@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.astuetz;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.astuetz.pagerslidingtabstrip.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PagerSlidingTabStrip extends HorizontalScrollView {
	private static final String TAG = "PagerSlidingTabStrip";
	private boolean hasIcon = false;

	public interface IconTabProvider {
		int getPageIconResId(int position);

		Drawable getPageIconDrawable(int position);
	}

	public interface NotificationTabProvider {
		boolean hasNotification(int position);
	}

	// @formatter:off
	private static final int[] ATTRS = new int[]{
			android.R.attr.textSize,
			android.R.attr.textColor
	};
	// @formatter:on

	private LinearLayout.LayoutParams defaultTabLayoutParams;
	private LinearLayout.LayoutParams expandedTabLayoutParams;

	private final PageListener pageListener = new PageListener();
	public OnPageChangeListener delegatePageListener;

	private LinearLayout tabsContainer;
	private ViewPager pager;

	private int tabCount;

	private int currentPosition = 0;
	private float currentPositionOffset = 0f;

	private Paint rectPaint;
	private Paint dividerPaint;

	private int indicatorColor = 0xFF666666;
	private int underlineColor = 0x1A000000;
	private int dividerColor = 0x1A000000;

	private boolean shouldExpand = false;
	private boolean textAllCaps = true;

	private int scrollOffset = 52;
	private int indicatorHeight = 8;
	private int underlineHeight = 2;
	private int dividerPadding = 12;
	private int tabPadding = 24;
	private int dividerWidth = 1;
	private boolean fitWindow = false;

	private int tabTextSize = 12;
	private int tabTextColor = 0xFF666666;
	private Typeface tabTypeface = null;
	private int tabTypefaceStyle = Typeface.BOLD;

	private int lastScrollX = 0;

	private int tabBackgroundResId = R.drawable.background_tab;

	private Locale locale;
	private int iconSize = dpToPx(24);

	private int iconColorFilter = 0;

	private int screenWidth = 0;
	private int linePaddingFromBottom = 0;

	//8C9BBE
	int colorFilterActive, colorFilterInActive;
	ValueAnimator tintAnimator, unTintAnimator;
	long animationPlayTime = 5000L;

	ImageView currentIcon, nextIcon;
	List<ImageView> icons = new ArrayList<>();
	TextView currentText, nextText;
	List<TextView> textViews = new ArrayList<>();

	private List<TextView> counters = new ArrayList<>();

	public PagerSlidingTabStrip(Context context) {
		this(context, null);
	}

	public PagerSlidingTabStrip(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public PagerSlidingTabStrip(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		setFillViewport(true);
		setWillNotDraw(false);

		tabsContainer = new LinearLayout(context);
		tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
		tabsContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		addView(tabsContainer);

		DisplayMetrics dm = getResources().getDisplayMetrics();

		scrollOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, scrollOffset, dm);
		indicatorHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, indicatorHeight, dm);
		underlineHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, underlineHeight, dm);
		dividerPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dividerPadding, dm);
		tabPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, tabPadding, dm);
		dividerWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dividerWidth, dm);
		tabTextSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, tabTextSize, dm);

		// get system attrs (android:textSize and android:textColor)

		TypedArray a = context.obtainStyledAttributes(attrs, ATTRS);

		tabTextSize = a.getDimensionPixelSize(0, tabTextSize);
		//noinspection ResourceType
		tabTextColor = a.getColor(1, tabTextColor);

		a.recycle();

		// get custom attrs

		a = context.obtainStyledAttributes(attrs, R.styleable.PagerSlidingTabStrip);

		indicatorColor = a.getColor(R.styleable.PagerSlidingTabStrip_pstsIndicatorColor, indicatorColor);
		underlineColor = a.getColor(R.styleable.PagerSlidingTabStrip_pstsUnderlineColor, underlineColor);
		dividerColor = a.getColor(R.styleable.PagerSlidingTabStrip_pstsDividerColor, dividerColor);
		indicatorHeight = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsIndicatorHeight, indicatorHeight);
		underlineHeight = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsUnderlineHeight, underlineHeight);
		dividerPadding = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsDividerPadding, dividerPadding);
		tabPadding = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsTabPaddingLeftRight, tabPadding);
		tabBackgroundResId = a.getResourceId(R.styleable.PagerSlidingTabStrip_pstsTabBackground, tabBackgroundResId);
		shouldExpand = a.getBoolean(R.styleable.PagerSlidingTabStrip_pstsShouldExpand, shouldExpand);
		scrollOffset = a.getDimensionPixelSize(R.styleable.PagerSlidingTabStrip_pstsScrollOffset, scrollOffset);
		textAllCaps = a.getBoolean(R.styleable.PagerSlidingTabStrip_pstsTextAllCaps, textAllCaps);

		a.recycle();

		rectPaint = new Paint();
		rectPaint.setAntiAlias(true);
		rectPaint.setStyle(Style.FILL);

		dividerPaint = new Paint();
		dividerPaint.setAntiAlias(true);
		dividerPaint.setStrokeWidth(dividerWidth);

		defaultTabLayoutParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
		expandedTabLayoutParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);

		if (locale == null) {
			locale = getResources().getConfiguration().locale;
		}


	}

	public void setColorFilters(int colorFilterActive,
	                            int colorFilterInActive,
	                            long animationPlayTime) {
		this.colorFilterActive = colorFilterActive;
		this.colorFilterInActive = colorFilterInActive;
		this.iconColorFilter = colorFilterInActive;
		this.animationPlayTime = animationPlayTime;

		tintAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), colorFilterInActive, colorFilterActive);
		unTintAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), colorFilterActive, colorFilterInActive);

		tintAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				if (nextIcon != null) {
					nextIcon.setColorFilter((int) animation.getAnimatedValue());
				}
				if (nextText != null) {
					nextText.setTextColor((int) animation.getAnimatedValue());
				}
			}
		});
		unTintAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				if (currentIcon != null) {
					currentIcon.setColorFilter((int) animation.getAnimatedValue());
				}
				if (currentText != null) {
					currentText.setTextColor((int) animation.getAnimatedValue());
				}
			}
		});
		tintAnimator.setDuration(animationPlayTime);
		unTintAnimator.setDuration(animationPlayTime);
	}


	public void setViewPager(ViewPager pager) {
		this.pager = pager;

		if (pager.getAdapter() == null) {
			throw new IllegalStateException("ViewPager does not have adapter instance.");
		}

		pager.addOnPageChangeListener(pageListener);
		getScreenDimensions();
		notifyDataSetChanged();
	}

	public void setOnPageChangeListener(OnPageChangeListener listener) {
		this.delegatePageListener = listener;
	}

	public void notifyDataSetChanged() {

		tabsContainer.removeAllViews();

		tabCount = pager.getAdapter().getCount();

		for (int i = 0; i < tabCount; i++) {

			if (pager.getAdapter() instanceof IconTabProvider) {
				int resId = ((IconTabProvider) pager.getAdapter()).getPageIconResId(i);
				CharSequence title = pager.getAdapter().getPageTitle(i);
				hasIcon = true;
				if (resId != 0) {
					addIconTab(i, resId);
				} else {
					if (title == null) {
						addIconTab(i, ((IconTabProvider) pager.getAdapter()).getPageIconDrawable(i));
					} else {
						addIconTabWithText(i, ((IconTabProvider) pager.getAdapter()).getPageIconDrawable(i), title.toString());
					}
				}
			} else if (pager.getAdapter() instanceof NotificationTabProvider && ((NotificationTabProvider) pager.getAdapter()).hasNotification(i)) {
				addTextTabWithNotification(i, pager.getAdapter().getPageTitle(i).toString());
			} else {
				addTextTab(i, pager.getAdapter().getPageTitle(i).toString());
			}

		}

		updateTabStyles();

		getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

			@SuppressWarnings("deprecation")
			@SuppressLint("NewApi")
			@Override
			public void onGlobalLayout() {

				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
					getViewTreeObserver().removeGlobalOnLayoutListener(this);
				} else {
					getViewTreeObserver().removeOnGlobalLayoutListener(this);
				}

				currentPosition = pager.getCurrentItem();
				scrollToChild(currentPosition, 0);
			}
		});

	}

	public boolean isFitWindow() {
		return fitWindow;
	}

	public void setFitWindow(boolean fitWindow) {
		this.fitWindow = fitWindow;
	}

	private void addTextTab(final int position, String title) {

		TextView tab = new TextView(getContext());
		tab.setText(title);
		tab.setGravity(Gravity.CENTER);
		tab.setSingleLine();

		addTab(position, tab);
		textViews.add(tab);
		if (iconColorFilter != 0) {
			tab.setTextColor(iconColorFilter);
		}
	}

	private void addTextTabWithNotification(final int position, String titleStr) {

		View tab = LayoutInflater.from(getContext()).inflate(R.layout.notification_text, null, false);

		TextView title = (TextView) tab.findViewById(R.id.title);
		TextView counter = (TextView) tab.findViewById(R.id.notification);

		counters.add(position, counter);
		title.setText(titleStr);
		textViews.add(title);
		addTab(position, tab);
		if (iconColorFilter != 0) {
			title.setTextColor(iconColorFilter);
		}
	}

	public void updateNotificationCounter(int index, int value) {
		if (value == 0) {
			counters.get(index).setVisibility(GONE);
		} else {
			counters.get(index).setVisibility(VISIBLE);
			counters.get(index).setText(String.valueOf(value));
		}
	}

	public void setLinePaddingFromBottom(int linePaddingFromBottom) {
		this.linePaddingFromBottom = linePaddingFromBottom;
	}

	private void addIconTab(final int position, int resId) {
		LinearLayout layout = new LinearLayout(getContext());
		layout.setGravity(Gravity.CENTER);
		ImageView tab = new ImageView(getContext());
		tab.setImageResource(resId);
		tab.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
		if (iconColorFilter != 0) {
			tab.setColorFilter(iconColorFilter);
		}
		layout.addView(tab);
		addTab(position, layout);
		icons.add(tab);
	}

	private void addIconTab(final int position, Drawable drawable) {
		LinearLayout layout = new LinearLayout(getContext());
		layout.setGravity(Gravity.CENTER);
		ImageView tab = new ImageView(getContext());
		tab.setImageDrawable(drawable);
		if (iconColorFilter != 0) {
			tab.setColorFilter(iconColorFilter);
		}
		tab.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
		layout.addView(tab);
		addTab(position, layout);
		icons.add(tab);
	}

	private void addIconTabWithText(final int position, Drawable drawable, String text) {
		LinearLayout layout = new LinearLayout(getContext());
		layout.setGravity(Gravity.CENTER);
		layout.setOrientation(LinearLayout.VERTICAL);
		if (fitWindow) {
			LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(screenWidth / tabCount, ViewGroup.LayoutParams.MATCH_PARENT);
			layoutParams.gravity = Gravity.CENTER;
			layout.setLayoutParams(layoutParams);
		}

		ImageView tab = new ImageView(getContext());
		tab.setImageDrawable(drawable);
		tab.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));

		TextView textView = new TextView(getContext());
		textView.setText(text);
		textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		textView.setTextColor(getTextColor());
		layout.addView(tab);
		layout.addView(textView);
		addTab(position, layout);
		icons.add(tab);
		textViews.add(textView);
		if (iconColorFilter != 0) {
			tab.setColorFilter(iconColorFilter);
			textView.setTextColor(iconColorFilter);
		}
	}

	private void addTab(final int position, View tab) {
		tab.setFocusable(true);
		tab.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				pager.setCurrentItem(position);
			}
		});

		tab.setPadding(tabPadding, 0, tabPadding, 0);
		tabsContainer.addView(tab, position, shouldExpand ? expandedTabLayoutParams : defaultTabLayoutParams);
	}

	private void updateTabStyles() {

		for (int i = 0; i < tabCount; i++) {

			View v = tabsContainer.getChildAt(i);

			v.setBackgroundResource(tabBackgroundResId);

			if (v instanceof LinearLayout) {
				v = v.findViewById(R.id.title);
			}

			if (v instanceof TextView) {

				TextView tab = (TextView) v;
				tab.setTextSize(TypedValue.COMPLEX_UNIT_PX, tabTextSize);
				tab.setTypeface(tabTypeface, tabTypefaceStyle);
				tab.setTextColor(tabTextColor);

				// setAllCaps() is only available from API 14, so the upper case is made manually if we are on a
				// pre-ICS-build
				if (textAllCaps) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
						tab.setAllCaps(true);
					} else {
						tab.setText(tab.getText().toString().toUpperCase(locale));
					}
				}
			}
		}

	}

	private void scrollToChild(int position, int offset) {

		if (tabCount == 0) {
			return;
		}

		int newScrollX = tabsContainer.getChildAt(position).getLeft() + offset;

		if (position > 0 || offset > 0) {
			newScrollX -= scrollOffset;
		}

		if (newScrollX != lastScrollX) {
			lastScrollX = newScrollX;
			scrollTo(newScrollX, 0);
		}

	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (isInEditMode() || tabCount == 0) {
			return;
		}

		final int height = getHeight();

		// draw indicator line

		rectPaint.setColor(indicatorColor);

		// default: line below current tab
		View currentTab = tabsContainer.getChildAt(currentPosition);
		float lineLeft = currentTab.getLeft();
		float lineRight = currentTab.getRight();

		// if there is an offset, start interpolating left and right coordinates between current and next tab
		if (currentPositionOffset > 0f && currentPosition < tabCount - 1) {

			View nextTab = tabsContainer.getChildAt(currentPosition + 1);
			final float nextTabLeft = nextTab.getLeft();
			final float nextTabRight = nextTab.getRight();

			lineLeft = (currentPositionOffset * nextTabLeft + (1f - currentPositionOffset) * lineLeft);
			lineRight = (currentPositionOffset * nextTabRight + (1f - currentPositionOffset) * lineRight);
		}

		canvas.drawRect(lineLeft, height - indicatorHeight - linePaddingFromBottom, lineRight, height - linePaddingFromBottom, rectPaint);

		// draw underline

		rectPaint.setColor(underlineColor);
		canvas.drawRect(0, height - underlineHeight, tabsContainer.getWidth(), height, rectPaint);

		// draw divider

		dividerPaint.setColor(dividerColor);
		for (int i = 0; i < tabCount - 1; i++) {
			View tab = tabsContainer.getChildAt(i);
			canvas.drawLine(tab.getRight(), dividerPadding, tab.getRight(), height - dividerPadding, dividerPaint);
		}
	}


	private class PageListener implements OnPageChangeListener {
		float prevOffset = 0;

		@Override
		public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

			currentPosition = position;
			currentPositionOffset = positionOffset;

			scrollToChild(position, (int) (positionOffset * tabsContainer.getChildAt(position).getWidth()));

			invalidate();

			if (delegatePageListener != null) {
				delegatePageListener.onPageScrolled(position, positionOffset, positionOffsetPixels);
			}
			if (tintAnimator != null) {
				float delta = positionOffset != 0 ? positionOffset - prevOffset : 0;
				prevOffset = positionOffset;
				if (delta > 0) {
					if (icons.size() > 0) {
						currentIcon = icons.get(position);
						nextIcon = icons.get(position + 1);
					}
					if (textViews.size() > 0) {
						currentText = textViews.get(position);
						nextText = textViews.get(position + 1);
					}
					tintAnimator.setCurrentPlayTime((long) (positionOffset * animationPlayTime));
					unTintAnimator.setCurrentPlayTime((long) (positionOffset * animationPlayTime));
				} else if (delta < 0) {
					if (icons.size() > 0) {
						currentIcon = icons.get(position + 1);
						nextIcon = icons.get(position);
					}
					if (textViews.size() > 0) {
						currentText = textViews.get(position + 1);
						nextText = textViews.get(position);
					}
					unTintAnimator.setCurrentPlayTime((long) ((1 - positionOffset) * animationPlayTime));
					tintAnimator.setCurrentPlayTime((long) ((1 - positionOffset) * animationPlayTime));
				}
				if (delta == 0) {
					icons.get(pager.getCurrentItem()).setColorFilter(Color.WHITE);
					textViews.get(pager.getCurrentItem()).setTextColor(Color.WHITE);
				}
			}

		}

		@Override
		public void onPageScrollStateChanged(int state) {
			if (state == ViewPager.SCROLL_STATE_IDLE) {
				scrollToChild(pager.getCurrentItem(), 0);
			}

			if (delegatePageListener != null) {
				delegatePageListener.onPageScrollStateChanged(state);
			}
		}

		@Override
		public void onPageSelected(int position) {
			if (delegatePageListener != null) {
				delegatePageListener.onPageSelected(position);
			}
		}

	}

	public void setIndicatorColor(int indicatorColor) {
		this.indicatorColor = indicatorColor;
		invalidate();
	}

	public void setIndicatorColorResource(int resId) {
		this.indicatorColor = getResources().getColor(resId);
		invalidate();
	}

	public int getIconColorFilter() {
		return iconColorFilter;
	}

	public void setIconColorFilter(int iconColorFilter) {
//		this.iconColorFilter = iconColorFilter;
		this.iconColorFilter = Color.parseColor("#333333");
	}

	public int getIndicatorColor() {
		return this.indicatorColor;
	}

	public void setIndicatorHeight(int indicatorLineHeightPx) {
		this.indicatorHeight = indicatorLineHeightPx;
		invalidate();
	}

	public int getIndicatorHeight() {
		return indicatorHeight;
	}

	public void setUnderlineColor(int underlineColor) {
		this.underlineColor = underlineColor;
		invalidate();
	}

	public void setUnderlineColorResource(int resId) {
		this.underlineColor = getResources().getColor(resId);
		invalidate();
	}

	public int getUnderlineColor() {
		return underlineColor;
	}

	public void setDividerColor(int dividerColor) {
		this.dividerColor = dividerColor;
		invalidate();
	}

	public void setDividerColorResource(int resId) {
		this.dividerColor = getResources().getColor(resId);
		invalidate();
	}

	public int getDividerColor() {
		return dividerColor;
	}

	public void setUnderlineHeight(int underlineHeightPx) {
		this.underlineHeight = underlineHeightPx;
		invalidate();
	}

	public int getUnderlineHeight() {
		return underlineHeight;
	}

	public void setDividerPadding(int dividerPaddingPx) {
		this.dividerPadding = dividerPaddingPx;
		invalidate();
	}

	public int getDividerPadding() {
		return dividerPadding;
	}

	public void setScrollOffset(int scrollOffsetPx) {
		this.scrollOffset = scrollOffsetPx;
		invalidate();
	}

	public int getScrollOffset() {
		return scrollOffset;
	}

	public void setShouldExpand(boolean shouldExpand) {
		this.shouldExpand = shouldExpand;
		requestLayout();
	}

	public boolean getShouldExpand() {
		return shouldExpand;
	}

	public boolean isTextAllCaps() {
		return textAllCaps;
	}

	public void setAllCaps(boolean textAllCaps) {
		this.textAllCaps = textAllCaps;
	}

	public void setTextSize(int textSizePx) {
		this.tabTextSize = textSizePx;
		updateTabStyles();
	}

	public int getTextSize() {
		return tabTextSize;
	}

	public void setTextColor(int textColor) {
		this.tabTextColor = textColor;
		updateTabStyles();
	}

	public void setTextColorResource(int resId) {
		this.tabTextColor = getResources().getColor(resId);
		updateTabStyles();
	}

	public int getTextColor() {
		return tabTextColor;
	}

	public void setTypeface(Typeface typeface, int style) {
		this.tabTypeface = typeface;
		this.tabTypefaceStyle = style;
		updateTabStyles();
	}

	public void setTabBackground(int resId) {
		this.tabBackgroundResId = resId;
	}

	public int getTabBackground() {
		return tabBackgroundResId;
	}

	public void setTabPaddingLeftRight(int paddingPx) {
		this.tabPadding = paddingPx;
		updateTabStyles();
	}

	public int getTabPaddingLeftRight() {
		return tabPadding;
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		SavedState savedState = (SavedState) state;
		super.onRestoreInstanceState(savedState.getSuperState());
		currentPosition = savedState.currentPosition;
		requestLayout();
	}

	@Override
	public Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		SavedState savedState = new SavedState(superState);
		savedState.currentPosition = currentPosition;
		return savedState;
	}

	static class SavedState extends BaseSavedState {
		int currentPosition;

		public SavedState(Parcelable superState) {
			super(superState);
		}

		private SavedState(Parcel in) {
			super(in);
			currentPosition = in.readInt();
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeInt(currentPosition);
		}

		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			@Override
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			@Override
			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}

	public int dpToPx(int dp) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
		                                       dp,
		                                       getContext().getResources().getDisplayMetrics());
	}

	public void getScreenDimensions() {
		WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		screenWidth = size.x;
	}

}
