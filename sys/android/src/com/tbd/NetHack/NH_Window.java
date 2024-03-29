package com.tbd.NetHack;

import java.util.Set;
import android.app.Activity;

public interface NH_Window
{
	public int id();
	public void show(boolean bBlocking);
	public void destroy();
	public void clear();
	public void printString(TextAttr attr, String str, int append);
	public int handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount, boolean bSoftInput);
	public void setContext(Activity context);
	public boolean isBlocking();
	public String getTitle();
}
