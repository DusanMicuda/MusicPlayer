package com.micudasoftware.musicplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;

import org.jetbrains.annotations.NotNull;

public class BottomNavigationBehavior extends AppBarLayout.ScrollingViewBehavior {

    public BottomNavigationBehavior() {
        super();
    }

    public BottomNavigationBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull @NotNull CoordinatorLayout coordinatorLayout, @NonNull @NotNull View child, @NonNull @NotNull View directTargetChild, @NonNull @NotNull View target, int nestedScrollAxes, int type) {
        return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL;
    }

    @Override
    public void onNestedPreScroll(@NonNull @NotNull CoordinatorLayout coordinatorLayout, @NonNull @NotNull View child, @NonNull @NotNull View target, int dx, int dy, @NonNull @NotNull int[] consumed, int type) {
        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type);
        float bottom;
        float containerHeight = child.getRootView().findViewById(R.id.container).getHeight();
        RecyclerView recyclerView = child.getRootView().findViewById(R.id.recyclerView);
        GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager.findLastVisibleItemPosition() == layoutManager.getItemCount() -1)
            bottom = layoutManager.findViewByPosition(layoutManager.findLastVisibleItemPosition())
                    .getBottom() + recyclerView.getY();
        else
            bottom = containerHeight;

        float translation = Float.max(
                containerHeight - recyclerView.getPaddingBottom(),
                Float.min(bottom, MainActivity.navigationView.getY() + ((float)dy)/2.0f));
        if (dy!= 0)
            MainActivity.navigationView.setY(translation);
    }

    public static void setToDefault() {
        float containerHeight = MainActivity.navigationView.getRootView().findViewById(R.id.container).getHeight();
        float bottom;
        RecyclerView recyclerView = MainActivity.navigationView.getRootView().findViewById(R.id.recyclerView);
        GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager.findLastVisibleItemPosition() == layoutManager.getItemCount() -1)
            bottom = layoutManager.findViewByPosition(layoutManager.findLastVisibleItemPosition())
                    .getBottom() + recyclerView.getY();
        else
            bottom = containerHeight - recyclerView.getPaddingBottom();
        MainActivity.navigationView.setY(Float.max(bottom, containerHeight - recyclerView.getPaddingBottom()));
    }
}
