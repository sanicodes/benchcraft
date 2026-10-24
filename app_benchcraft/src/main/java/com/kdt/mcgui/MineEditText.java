package com.kdt.mcgui;

import android.content.*;
import android.util.*;

import androidx.core.content.res.ResourcesCompat;

import git.artdeell.mojo.R;

public class MineEditText extends androidx.appcompat.widget.AppCompatEditText {
	public MineEditText(Context ctx) {
		super(ctx);
		init();
	}

	public MineEditText(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.background_input, null));
		int horizontalPadding = getResources().getDimensionPixelSize(R.dimen.padding_heavy);
		setPaddingRelative(horizontalPadding, 0, horizontalPadding, 0);
	}
}
