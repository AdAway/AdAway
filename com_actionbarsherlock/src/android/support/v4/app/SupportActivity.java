package android.support.v4.app;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import com.actionbarsherlock.internal.app.SherlockActivity;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.view.ActionMode;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * <p>Instances of this interface represent an activity provided by the support
 * library (e.g., {@link FragmentActivity}).</p>
 *
 * <p>Provided are all of the methods which would be available if you were
 * accessing the underlying activity directly and you can safely assume that
 * any instances of this interface can be cast to an {@link Activity}. It is
 * preferred, however, that you call {@link #asActivity()} instead.</p>
 */
public interface SupportActivity extends SherlockActivity {
    public static abstract class InternalCallbacks {
        abstract Handler getHandler();
        abstract FragmentManagerImpl getFragments();
        abstract LoaderManagerImpl getLoaderManager(int index, boolean started, boolean create);
        abstract void invalidateSupportFragmentIndex(int index);
        abstract boolean getRetaining();
    }

    InternalCallbacks getInternalCallbacks();
    Activity asActivity();

    /*** Activity methods ***/
    void addContentView(View view, ViewGroup.LayoutParams params);
    void closeContextMenu();
    void closeOptionsMenu();
    PendingIntent createPendingResult(int requestCode, Intent data, int flags);
    void dismissDialog(int id);
    boolean dispatchKeyEvent(KeyEvent event);
    boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event);
    boolean dispatchTouchEvent(MotionEvent ev);
    boolean dispatchTrackballEvent(MotionEvent ev);
    View findViewById(int id);
    void finish();
    void finishActivity(int requestCode);
    void finishActivityFromChild(Activity child, int requestCode);
    void finishFromChild(Activity child);
    Application getApplication();
    ComponentName getCallingActivity();
    String getCallingPackage();
    int getChangingConfigurations();
    ComponentName getComponentName();
    View getCurrentFocus();
    Intent getIntent();
    Object getLastNonConfigurationInstance();
    LayoutInflater getLayoutInflater();
    String getLocalClassName();
    MenuInflater getMenuInflater();
    Activity getParent();
    SharedPreferences getPreferences(int mode);
    int getRequestedOrientation();
    Object getSystemService(String name);
    int getTaskId();
    CharSequence getTitle();
    int getTitleColor();
    int getVolumeControlStream();
    Window getWindow();
    WindowManager getWindowManager();
    boolean hasWindowFocus();
    boolean isChild();
    boolean isFinishing();
    boolean isTaskRoot();
    Cursor managedQuery(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder);
    boolean moveTaskToBack(boolean nonRoot);
    void onConfigurationChanged(Configuration newConfig);
    void onContentChanged();
    boolean onContextItemSelected(android.view.MenuItem item);
    void onContextMenuClosed(android.view.Menu menu);
    void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo);
    CharSequence onCreateDescription();
    boolean onCreateOptionsMenu(android.view.Menu menu);
    boolean onCreatePanelMenu(int featureId, android.view.Menu menu);
    View onCreatePanelView(int featureId);
    boolean onCreateThumbnail(Bitmap outBitmap, Canvas canvas);
    View onCreateView(String name, Context context, AttributeSet attrs);
    boolean onKeyDown(int keyCode, KeyEvent event);
    boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event);
    boolean onKeyUp(int keyCode, KeyEvent event);
    void onLowMemory();
    boolean onMenuItemSelected(int featureId, android.view.MenuItem item);
    boolean onMenuOpened(int featureId, android.view.Menu menu);
    boolean onOptionsItemSelected(android.view.MenuItem item);
    void onOptionsMenuClosed(android.view.Menu menu);
    void onPanelClosed(int featureId, android.view.Menu menu);
    boolean onPrepareOptionsMenu(android.view.Menu menu);
    boolean onPreparePanel(int featureId, View view, android.view.Menu menu);
    Object onRetainNonConfigurationInstance();
    boolean onSearchRequested();
    boolean onTouchEvent(MotionEvent event);
    boolean onTrackballEvent(MotionEvent event);
    void onUserInteraction();
    void onWindowAttributesChanged(WindowManager.LayoutParams params);
    void onWindowFocusChanged(boolean hasFocus);
    void openContextMenu(View view);
    void openOptionsMenu();
    void registerForContextMenu(View view);
    void removeDialog(int id);
    boolean requestWindowFeature(int featureId);
    void runOnUiThread(Runnable action);
    void setContentView(int layoutResId);
    void setContentView(View view);
    void setContentView(View view, ViewGroup.LayoutParams params);
    void setDefaultKeyMode(int mode);
    void setFeatureDrawable(int featureId, Drawable drawable);
    void setFeatureDrawableAlpha(int featureId, int alpha);
    void setFeatureDrawableResource(int featureId, int resId);
    void setFeatureDrawableUri(int featureId, Uri uri);
    void setIntent(Intent newIntent);
    void setProgress(int progress);
    void setProgressBarIndeterminate(boolean indeterminate);
    void setProgressBarIndeterminateVisibility(boolean visible);
    void setProgressBarVisibility(boolean visible);
    void setRequestedOrientation(int requestedOrientation);
    void setResult(int resultCode);
    void setResult(int resultCode, Intent data);
    void setSecondaryProgress(int secondaryProgress);
    void setTitle(int titleId);
    void setTitle(CharSequence title);
    void setTitleColor(int textColor);
    void setVisible(boolean visible);
    void setVolumeControlStream(int streamType);
    void showDialog(int id);
    void startActivity(Intent intent);
    void startActivityForResult(Intent intent, int requestCode);
    void startActivityFromChild(Activity child, Intent intent, int requestCode);
    boolean startActivityIfNeeded(Intent intent, int requestCode);
    void startManagingCursor(Cursor c);
    boolean startNextMatchingActivity(Intent intent);
    void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchDate, boolean globalSearch);
    void stopManagingCursor(Cursor c);
    void takeKeyEvents(boolean get);
    void unregisterForContextMenu(View view);

    /*** ContextThemeWrapper methods ***/
    //Object getSystemService(String name);
    Resources.Theme getTheme();
    void setTheme(int resId);

    /*** ContextWrapper methods ***/
    //void attachBaseContext(Context base);
    boolean bindService(Intent service, ServiceConnection conn, int flags);
    int checkCallingOrSelfPermission(String permission);
    int checkCallingOrSelfUriPermission(Uri uri, int modeFlags);
    int checkCallingPermission(String permission);
    int checkCallingUriPermission(Uri uri, int modeFlags);
    int checkPermission(String permission, int pid, int uid);
    int checkUriPermission(Uri uri, int pid, int uid, int modeFlags);
    int checkUriPermission(Uri uri, String readPermission, String writePermission, int pid, int uid, int modeFlags);
    @Deprecated void clearWallpaper() throws IOException;
    Context createPackageContext(String packageName, int flags) throws NameNotFoundException;
    String[] databaseList();
    boolean deleteDatabase(String name);
    boolean deleteFile(String name);
    void enforceCallingOrSelfPermission(String permission, String message);
    void enforceCallingOrSelfUriPermission(Uri uri, int modeFlags, String message);
    void enforceCallingPermission(String permission, String message);
    void enforceCallingUriPermission(Uri uri, int modeFlags, String message);
    void enforcePermission(String permission, int pid, int uid, String message);
    void enforceUriPermission(Uri uri, int pid, int uid, int modeFlags, String message);
    void enforceUriPermission(Uri uri, String readPermission, String writePermission, int pid, int uid, int modeFlags, String message);
    String[] fileList();
    Context getApplicationContext();
    ApplicationInfo getApplicationInfo();
    AssetManager getAssets();
    Context getBaseContext();
    File getCacheDir();
    ClassLoader getClassLoader();
    ContentResolver getContentResolver();
    File getDatabasePath(String name);
    File getDir(String name, int mode);
    File getFileStreamPath(String name);
    File getFilesDir();
    Looper getMainLooper();
    String getPackageCodePath();
    PackageManager getPackageManager();
    String getPackageName();
    String getPackageResourcePath();
    Resources getResources();
    SharedPreferences getSharedPreferences(String name, int mode);
    //Object getSystemService(String name);
    //Resources.Theme getTheme();
    Drawable getWallpaper();
    int getWallpaperDesiredMinimumHeight();
    int getWallpaperDesiredMinimumWidth();
    void grantUriPermission(String toPackage, Uri uri, int modeFlags);
    boolean isRestricted();
    FileInputStream openFileInput(String name) throws FileNotFoundException;
    FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException;
    SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory);
    @Deprecated Drawable peekWallpaper();
    Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter);
    Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String broadcastPermission, Handler scheduler);
    void removeStickyBroadcast(Intent intent);
    void revokeUriPermission(Uri uri, int modeFlags);
    void sendBroadcast(Intent intent);
    void sendBroadcast(Intent intent, String receiverPermission);
    void sendOrderedBroadcast(Intent intent, String receiverPermission, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras);
    void sendOrderedBroadcast(Intent intent, String receiverPermission);
    void sendStickyBroadcast(Intent intent);
    //void setTheme(int resid);
    void setWallpaper(Bitmap bitmap) throws IOException;
    void setWallpaper(InputStream data) throws IOException;
    //void startActivity(Intent intent);
    boolean startInstrumentation(ComponentName className, String profileFile, Bundle arguments);
    ComponentName startService(Intent service);
    boolean stopService(Intent name);
    void unbindService(ServiceConnection conn);
    void unregisterReceiver(BroadcastReceiver receiver);

    /*** Context methods ***/
    String getString(int resId);
    String getString(int resId, Object... formatArgs);
    CharSequence getText(int resId);
    //boolean isRestricted();
    TypedArray obtainStyledAttributes(int[] attrs);
    TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs);
    TypedArray obtainStyledAttributes(int resId, int[] attrs);
    TypedArray obtainStyledAttributes(AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes);

    /*** Activity methods (emulated API 5+) ***/
    void onBackPressed();

    /*** Activity methods (emulated API 11+) ***/
    void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args);
    ActionBar getSupportActionBar(); //getActionBar()
    FragmentManager getSupportFragmentManager(); //getFragmentManager()
    LoaderManager getSupportLoaderManager(); //getLoaderManager()
    void invalidateOptionsMenu();
    void onActionModeFinished(ActionMode mode);
    void onActionModeStarted(ActionMode mode);
    void onAttachFragment(Fragment fragment);
    boolean onCreateOptionsMenu(Menu menu);
    boolean onMenuItemSelected(int featureId, MenuItem item);
    boolean onOptionsItemSelected(MenuItem item);
    boolean onPrepareOptionsMenu(Menu menu);
    ActionMode onWindowStartingActionMode(ActionMode.Callback callback);
    void recreate();
    ActionMode startActionMode(ActionMode.Callback callback);
    void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode);

    /*** Parallel helper methods ***/
    boolean requestWindowFeature(long featureId);
    void setProgressBarIndeterminateVisibility(Boolean visible);
}
