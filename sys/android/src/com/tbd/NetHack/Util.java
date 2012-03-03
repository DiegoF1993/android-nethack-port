package com.tbd.NetHack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public class Util
{
	// ____________________________________________________________________________________
	static public View inflate(Context context, int layoutId)
	{
		LayoutInflater vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		return vi.inflate(layoutId, null);
	}
	
	// ____________________________________________________________________________________
	static public View inflate(Context context, int layoutId, View parent)
	{
		View v = inflate(context, layoutId);
		((ViewGroup)parent).addView(v);
		return v;
	}
	
	// ____________________________________________________________________________________
	static public View inflate(Activity context, int layoutId, int parentId)
	{
		View v = inflate(context, layoutId);
		((ViewGroup)context.findViewById(parentId)).addView(v);
		return v;
	}

	// ____________________________________________________________________________________
	public static Object stringToObject(String str)
	{
		return ObsoleteBase64.stringToObject(str);
	}

	// ____________________________________________________________________________________
	public static boolean hasPhysicalKeyboard(Context context)
	{
		int keyboard = context.getResources().getConfiguration().keyboard;
		return keyboard == Configuration.KEYBOARD_QWERTY;
	}
	
	// ____________________________________________________________________________________
	public static void showKeyboard(final Context context, final EditText input)
	{
		// Never explicitly open soft keyboard if physical keyboard is present 
		if(!hasPhysicalKeyboard(context))
		{
			input.post(new Runnable()
			{
				public void run()
				{
					InputMethodManager imm = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.showSoftInput(input, InputMethodManager.SHOW_FORCED);
				}
			});
		}
	}

	// ____________________________________________________________________________________
	public static void hideKeyboard(Context context, EditText input)
	{
		InputMethodManager imm = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
	}

	// ____________________________________________________________________________________
	public static void hideKeyboard(Context context, View view)
	{
		view.setFocusable(false);
		InputMethodManager imm = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
	}
	
	// ____________________________________________________________________________________
	public static int parseInt(String string, int def)
	{
		try
		{
			return Integer.parseInt(string);
		}
		catch(NumberFormatException e)
		{
		}
		return def;
	}
}
