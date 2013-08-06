package com.tbd.NetHack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Debug;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

public class NH_State
{
	private enum CmdMode
	{
		Panel,
		Keyboard,
	}

	private Activity mContext;
	private NetHackIO mIO;
	private NHW_Message mMessage;
	private NHW_Status mStatus;
	private NHW_Map mMap;
	private NH_GetLine mGetLine;
	private NH_Question mQuestion;
	private ArrayList<NH_Window> mWindows;
	private Tileset mTileset;
	private String mDataDir;
	private String mUsername;
	private CmdPanelLayout mCmdPanelLayout;
	private DPadOverlay mDPad;
	private boolean mIsDPadActive;
	private boolean mStickyKeyboard;
	private boolean mHideQuickKeyboard;
	private CmdMode mMode;
	private SoftKeyboard mKeyboard;
	private boolean mControlsVisible;
	private boolean mNumPad;
	
	// ____________________________________________________________________________________
	public NH_State(NetHack context)
	{
		mIO = new NetHackIO(this);
		mTileset = new Tileset();
		mWindows = new ArrayList<NH_Window>();
		mGetLine = new NH_GetLine(mIO, this);
		mQuestion = new NH_Question(mIO, this);
		mMessage = new NHW_Message(context, mIO);
		mStatus = new NHW_Status(context, mIO);
		mMap = new NHW_Map(context, mTileset, mStatus, this);
		mCmdPanelLayout = (CmdPanelLayout)context.findViewById(R.id.cmdPanelLayout1);
		mDPad = new DPadOverlay(this);
		mKeyboard = new SoftKeyboard(context, this);
		mMode = CmdMode.Panel;

		setContext(context);
	}

	// ____________________________________________________________________________________
	public void setContext(NetHack context)
	{
		mContext = context;
		for(NH_Window w : mWindows)
			w.setContext(context);
		mGetLine.setContext(context);
		mQuestion.setContext(context);
		mMessage.setContext(context);
		mStatus.setContext(context);
		mCmdPanelLayout.setContext(context, this);
		mDPad.setContext(context);
		mMap.setContext(context);
	}

	// ____________________________________________________________________________________
	public void startNetHack(String path)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		
		mUsername = prefs.getString("username", "");
		mDataDir = path;
		mIO.start();

		preferencesUpdated();
		updateVisibleState();

		mMap.loadZoomLevel();
	}

	// ____________________________________________________________________________________
	public void setLastUsername(String username)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		prefs.edit().putString("lastUsername", username).commit();
	}
	
	// ____________________________________________________________________________________
	private String getLastUsername()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		return prefs.getString("lastUsername", "");
	}
	
	// ____________________________________________________________________________________
	public void onConfigurationChanged(Configuration newConfig)
	{
		if(mMode == CmdMode.Keyboard)
		{
			// Since the keyboard refuses to change its layout when the orientation changes
			// we recreate a new keyboard every time
			hideKeyboard();
			showKeyboard();
		}
		
		mCmdPanelLayout.setOrientation(newConfig.orientation);
		mDPad.setOrientation(newConfig.orientation);
	}

	// ____________________________________________________________________________________
	public void preferencesUpdated()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

		mCmdPanelLayout.preferencesUpdated(prefs);
		mDPad.preferencesUpdated(prefs);

		if(mMode == CmdMode.Panel)
			mCmdPanelLayout.show();

		String tilesetName = prefs.getString("tileset", "default_32");
		mTileset.updateTileset(tilesetName, mContext.getResources());

		String pickupTypes = prefs.getString("autoPickupTypes", "");
		boolean bAutoPickup = prefs.getBoolean("autoPickup", false);
		boolean bAutoMenu = prefs.getBoolean("automenu", true);
		mIO.sendFlags(bAutoMenu, bAutoPickup, pickupTypes);

		int flag = prefs.getBoolean("fullscreen", false) ? WindowManager.LayoutParams.FLAG_FULLSCREEN : 0;		
		mContext.getWindow().setFlags(flag, WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}

	// ____________________________________________________________________________________
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		mCmdPanelLayout.onCreateContextMenu(menu, v, menuInfo);
	}

	// ____________________________________________________________________________________
	public void onContextItemSelected(android.view.MenuItem item)
	{
		mCmdPanelLayout.onContextItemSelected(item);
	}

	// ____________________________________________________________________________________
	public boolean handleKeyDown(char ch, int nhKey, int keyCode, Set<Input.Modifier> modifiers, int repeatCount, boolean bSoftInput)
	{
		if(keyCode == KeyEvent.KEYCODE_BACK && isKeyboardMode())
		{
			hideKeyboard();
			return true;
		}
		
		int ret = mQuestion.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput);

		for(int i = mWindows.size() - 1; ret == 0 && i >= 0; i--)
		{
			NH_Window w = mWindows.get(i);
			ret = w.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput);
		}

		if(ret == 0)
			ret = mGetLine.handleKeyDown(ch, nhKey, keyCode, modifiers, repeatCount, bSoftInput);

		if(ret == 1)
			return true;
		if(ret == 2)// let system handle
			return false;
				
		if(keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME)
		{
			if(mMode == CmdMode.Keyboard)
			{
				hideKeyboard();
				return true;
			}
			
			if(mIsDPadActive)
				return sendKeyCmd('\033');
		}
		else if(keyCode == KeyAction.Keyboard)
		{
			if(repeatCount == 0)
			{
				mStickyKeyboard = true;
				toggleKeyboard();
			}
			else if(mMode == CmdMode.Keyboard)
				mStickyKeyboard = false;
			return true;
		}
		else if(keyCode == KeyAction.Control || keyCode == KeyAction.Meta)
		{
			if(repeatCount == 0 && !Util.hasPhysicalKeyboard(mContext))
			{
				if(mMode != CmdMode.Keyboard)
					mHideQuickKeyboard = true;
				showKeyboard();
				if(keyCode == KeyAction.Control)
					setCtrlKeyboard();
				else
					setMetaKeyboard();
			}
			return true;
		}
		if(DEBUG.runTrace() && keyCode == KeyEvent.KEYCODE_BACK)
			Debug.stopMethodTracing();
		return sendKeyCmd(nhKey);
	}
	
	// ____________________________________________________________________________________
	public boolean handleKeyUp(int keyCode)
	{
		if(mMap.handleKeyUp(keyCode))
			return true;

		if(keyCode == KeyAction.Keyboard)
		{
			if(!mStickyKeyboard && mMode == CmdMode.Keyboard)
				hideKeyboard();
			mStickyKeyboard = false;
			return true;
		}
		else if(keyCode == KeyAction.Control || keyCode == KeyAction.Meta)
		{
			if(mMode == CmdMode.Keyboard)
			{
				if(mHideQuickKeyboard)
					hideKeyboard();
				else
					setQwertyKeyboard();
			}
					
			mHideQuickKeyboard = false;
			return true;
		}
		return false;
	}

	// ____________________________________________________________________________________
	public void setCursorPos(int wid, int x, int y)
	{
		NH_Window wnd = getWindow(wid);
		if(wnd == mMap)
			mMap.setCursorPos(x, y);
		else if(wnd == mStatus)
			mStatus.setRow(y);
		else if(wnd == mMessage)
			mMessage.setCursorPos(x);
	}

	// ____________________________________________________________________________________
	public void putString(int wid, int attr, String msg, int append, int hp, int hpMax)
	{
		NH_Window wnd = getWindow(wid);
		if(wnd == null)
		{
			Log.print("[no wnd] " + msg);
			mMessage.printString(TextAttr.fromNative(attr), msg, append);
		}
		else
			wnd.printString(TextAttr.fromNative(attr), msg, append);
		if(mMap != null)
			mMap.setHealthLevel(hp, hpMax);
	}

	// ____________________________________________________________________________________
	public void rawPrint(int attr, String msg)
	{
		mMessage.printString(TextAttr.fromNative(attr), msg, 0);
	}

	// ____________________________________________________________________________________
	public void printTile(int wid, int x, int y, int tile, int ch, int col, int special)
	{
		mMap.printTile(x, y, tile, ch, col, special);
	}

	// ____________________________________________________________________________________
	public void ynFunction(String question, byte[] choices, int def)
	{
		mQuestion.show(mContext, question, choices, def);
	}

	// ____________________________________________________________________________________
	public void getLine(String title, int nMaxChars)
	{
		mGetLine.show(mContext, title, nMaxChars);
	}

	// ____________________________________________________________________________________
	public void askName(int nMaxChars, String[] saves) {
		String last = getLastUsername();
		List<String> list = new ArrayList<String>();
		for(String s : saves)
		{
			if(last.equals(s))
				list.add(0, s);
			else
				list.add(s);
		}
		mGetLine.showWhoAreYou(mContext, nMaxChars, list);
	}
	
	// ____________________________________________________________________________________
	public void createWindow(int wid, int type)
	{
		switch(type)
		{
		case 1: // #define NHW_MESSAGE 1
			mMessage.setId(wid);
			mWindows.add(mMessage);
		break;

		case 2: // #define NHW_STATUS 2
			mStatus.setId(wid);
			mWindows.add(mStatus);
		break;

		case 3: // #define NHW_MAP 3
			mMap.setId(wid);
			mWindows.add(mMap);
		break;

		case 4: // #define NHW_MENU 4
			mWindows.add(new NHW_Menu(wid, mContext, mIO, mTileset));
		break;

		case 5: // #define NHW_TEXT 5
			mWindows.add(new NHW_Text(wid, mContext, mIO));
		break;
		}
	}

	// ____________________________________________________________________________________
	public NH_Window getWindow(int wid)
	{
		int i = getWindowI(wid);
		return i >= 0 ? mWindows.get(i) : null;
	}
	
	// ____________________________________________________________________________________
	public int getWindowI(int wid)
	{
		for(int i = 0; i < mWindows.size(); i++)
			if(mWindows.get(i).id() == wid)
				return i;
		return -1;
	}
	
	// ____________________________________________________________________________________
	public NH_Window toFront(int wid)
	{
		int i = getWindowI(wid);
		NH_Window w = null;
		if(i >= 0)
		{
			w = mWindows.get(i);
			if(i < mWindows.size() - 1)
			{
				mWindows.remove(i);
				mWindows.add(w);
			}
		}
		return w;
	}
	
	// ____________________________________________________________________________________
	public void displayWindow(final int wid, final int bBlocking)
	{
		NH_Window win = toFront(wid);
		if(win != null)
			win.show(bBlocking != 0);
	}

	// ____________________________________________________________________________________
	public void clearWindow(final int wid, final int isRogueLevel)
	{
		NH_Window wnd = getWindow(wid);
		if(wnd != null)
		{
			wnd.clear();
			if(wnd == mMap)
				mMap.setRogueLevel(isRogueLevel != 0);
		}
	}

	// ____________________________________________________________________________________
	public void destroyWindow(final int wid)
	{
		int i = getWindowI(wid);
		mWindows.get(i).hide();
		mWindows.remove(i);
	}

	// ____________________________________________________________________________________
	public void startMenu(final int wid)
	{
		((NHW_Menu)getWindow(wid)).startMenu();
	}

	// ____________________________________________________________________________________
	public void addMenu(int wid, int tile, int id, int acc, int groupAcc, int attr, String text, int bSelected)
	{
		((NHW_Menu)getWindow(wid)).addMenu(tile, id, acc, groupAcc, TextAttr.fromNative(attr), text, bSelected);
	}

	// ____________________________________________________________________________________
	public void endMenu(int wid, String prompt)
	{
		((NHW_Menu)getWindow(wid)).endMenu(prompt);
	}

	// ____________________________________________________________________________________
	public void selectMenu(int wid, int how)
	{
		((NHW_Menu)toFront(wid)).selectMenu(NHW_Menu.SelectMode.fromInt(how));
	}

	// ____________________________________________________________________________________
	public void cliparound(int x, int y, int playerX, int playerY)
	{
		mMap.cliparound(x, y, playerX, playerY);
	}

	// ____________________________________________________________________________________
	public void askDirection()
	{
		showDPad();
	}

	// ____________________________________________________________________________________
	public void showPrevMessage()
	{
		mMessage.showPrev();
	}

	// ____________________________________________________________________________________
	public void showLog()
	{
		mMessage.showLog();
	}

	// ____________________________________________________________________________________
	public void editOpts()
	{
	}
	
	// ____________________________________________________________________________________
	public void lockMouse()
	{
		mMap.lockMouse();
	}

	// ____________________________________________________________________________________
	public String getDataDir()
	{
		return mDataDir;
	}

	// ____________________________________________________________________________________
	public String getUsername()
	{
		return mUsername;
	}

	// ____________________________________________________________________________________
	public void saveAndQuit()
	{
		mIO.saveAndQuit();
	}

	// ____________________________________________________________________________________
	public void saveState()
	{
		mIO.saveState();
	}

	// ____________________________________________________________________________________
	public Handler getHandler()
	{
		return mIO.getHandler();
	}

	// ____________________________________________________________________________________
	public void waitReady()
	{
		mIO.waitReady();
	}

	// ____________________________________________________________________________________
	public boolean sendKeyCmd(int key)
	{
		if(key <= 0 || key > 0xff)
			return false;
		hideDPad();
		mIO.sendKeyCmd((char)key);
		return true;
	}

	// ____________________________________________________________________________________
	public boolean sendDirKeyCmd(int key)
	{
		if(key <= 0 || key > 0xff)
			return false;
		if(mIsDPadActive)
			mIO.sendKeyCmd((char)key);
		else
			mIO.sendDirKeyCmd((char)key);
		hideDPad();
		return true;
	}

	// ____________________________________________________________________________________
	public void sendPosCmd(int x, int y)
	{
		hideDPad();
		mIO.sendPosCmd(x, y);
	}


	// ____________________________________________________________________________________
	public void clickCursorPos()
	{
		mMap.onCursorPosClicked();
	}

	// ____________________________________________________________________________________
	public void showControls()
	{
		mControlsVisible = true;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void hideControls()
	{
		mControlsVisible = false;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void showKeyboard()
	{
		mMode = CmdMode.Keyboard;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void hideKeyboard()
	{
		mMode = CmdMode.Panel;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void toggleKeyboard()
	{
		if(mMode == CmdMode.Panel)
			showKeyboard();
		else
			hideKeyboard();
	}

	// ____________________________________________________________________________________
	public void setMetaKeyboard()
	{
		mKeyboard.setMetaKeyboard();
	}

	// ____________________________________________________________________________________
	public void setQwertyKeyboard()
	{
		mKeyboard.setQwertyKeyboard();
	}

	// ____________________________________________________________________________________
	public void setCtrlKeyboard()
	{
		mKeyboard.setCtrlKeyboard();
	}

	// ____________________________________________________________________________________
	public boolean isKeyboardMode()
	{
		return mMode == CmdMode.Keyboard;
	}
	
	// ____________________________________________________________________________________
	public void showDPad()
	{
		mIsDPadActive = true;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void hideDPad()
	{
		mIsDPadActive = false;
		updateVisibleState();
	}

	// ____________________________________________________________________________________
	public void updateVisibleState()
	{
		if(mControlsVisible)
		{
			if(mMode == CmdMode.Panel)
			{
				mKeyboard.hide();
				if(mIsDPadActive)
				{
					mDPad.setVisible(true);
					mCmdPanelLayout.hide();
				}
				else
				{
					mDPad.setVisible(false);
					mCmdPanelLayout.show();
				}
			}
			else
			{
				mKeyboard.show();
				mCmdPanelLayout.hide();
				//mDPad.setVisible(false);
				mDPad.forceHide();
			}
		}
		else
		{
			mCmdPanelLayout.hide();
			mKeyboard.hide();
			mDPad.forceHide();
		}
	}

	// ____________________________________________________________________________________
	public void viewAreaCanged(Rect viewRect)
	{
		mMap.viewAreaChanged(viewRect);
	}

	// ____________________________________________________________________________________
	public void setNumPadOption(boolean numPadOn) {
		mNumPad = numPadOn;
		mDPad.updateNumPadState();
	}

	// ____________________________________________________________________________________
	public boolean isNumPadOn() {
		return mNumPad;
	}

	// ____________________________________________________________________________________
	public void setWizardMode()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
		mCmdPanelLayout.wizardUpgrade(prefs);
	}
}
