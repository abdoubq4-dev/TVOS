package com.halo.tvhome;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * tvOS grid: six 5:3 icons per row. The first row sits on a smoky glass dock low on the
 * screen (dock top at ~73% of the height, icons at ~76.5%), with the next row peeking in below.
 */
final class GridPanel extends ViewGroup {
    static final int COLS = 6;
    /** tvOS icon size and focus growth. */
    static final float TILE_W = 124, TILE_H = 74.6f, FOCUS_SCALE = 1.18f, TILE_RADIUS = 12;
    /**
     * Padding is chosen so a focused icon is the same distance from every dock edge: it grows
     * more sideways than vertically, so the sides get that difference as extra padding.
     */
    private static final float DOCK_PAD_Y = 19;
    private static final float FOCUS_GAP = DOCK_PAD_Y - TILE_H * (FOCUS_SCALE - 1) / 2;
    private static final float DOCK_PAD_X = FOCUS_GAP + TILE_W * (FOCUS_SCALE - 1) / 2;

    final int tileW = Ui.dp(TILE_W);
    final int tileH = Ui.dp(TILE_H);
    final int gapX = Ui.dp(25);
    final int pitch = tileH + Ui.dp(40);
    final int top = Ui.dp(413);
    private int left;
    final FrostDock dock;
    /** Concentric with a focused icon's corners: its scaled radius plus the even gap. */
    static final float DOCK_RADIUS = TILE_RADIUS * FOCUS_SCALE + FOCUS_GAP;
    final List<AppTile> tiles = new ArrayList<>();

    GridPanel(Context c) {
        super(c);
        setClipChildren(false);
        setClipToPadding(false);
        dock = new FrostDock(c);
        dock.radius = Ui.dp(DOCK_RADIUS);
        addView(dock);
    }

    void setTiles(List<AppTile> list) {
        removeAllViews();
        addView(dock);
        tiles.clear();
        tiles.addAll(list);
        for (int i = 0; i < list.size(); i++) {
            AppTile t = list.get(i);
            t.showLabel = false;  // tvOS-clean: no names under icons
            t.label.setAlpha(0f);
            addView(t.label);
            addView(t);
        }
        requestLayout();
    }

    int rowY(int row) {
        return top + row * pitch;
    }

    int rows() {
        return (tiles.size() + COLS - 1) / COLS;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        left = (w - (COLS * tileW + (COLS - 1) * gapX)) / 2;
        int tw = MeasureSpec.makeMeasureSpec(tileW, MeasureSpec.EXACTLY);
        int th = MeasureSpec.makeMeasureSpec(tileH, MeasureSpec.EXACTLY);
        int lw = MeasureSpec.makeMeasureSpec(tileW + gapX, MeasureSpec.EXACTLY);
        int lh = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (AppTile t : tiles) {
            t.measure(tw, th);
            t.label.measure(lw, lh);
        }
        dock.measure(MeasureSpec.makeMeasureSpec(w - 2 * left + Ui.dp(2 * DOCK_PAD_X), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tileH + Ui.dp(2 * DOCK_PAD_Y), MeasureSpec.EXACTLY));
        setMeasuredDimension(w, rowY(Math.max(1, rows())) + Ui.dp(80));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int dx = left - Ui.dp(DOCK_PAD_X), dy = top - Ui.dp(DOCK_PAD_Y);
        dock.layout(dx, dy, dx + dock.getMeasuredWidth(), dy + dock.getMeasuredHeight());
        for (int i = 0; i < tiles.size(); i++) {
            AppTile tile = tiles.get(i);
            int x = left + (i % COLS) * (tileW + gapX);
            int y = rowY(i / COLS);
            tile.layout(x, y, x + tileW, y + tileH);
            View lab = tile.label;
            int lx = x - gapX / 2, ly = y + tileH + Ui.dp(6);
            lab.layout(lx, ly, lx + lab.getMeasuredWidth(), ly + lab.getMeasuredHeight());
        }
    }
}
