/***
  Copyright (c) 2011 CommonsWare, LLC
  
  Licensed under the Apache License, Version 2.0 (the "License"); you may
  not use this file except in compliance with the License. You may obtain
  a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

package com.commonsware.cwac.wakeful;

import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.XmlResourceParser;
import android.util.Log;
import com.commonsware.cwac.wakeful.WakefulIntentService.AlarmListener;

public class AlarmReceiver extends BroadcastReceiver {
  private static final String WAKEFUL_META_DATA="com.commonsware.cwac.wakeful";
  
  @Override
  public void onReceive(Context ctxt, Intent intent) {
    AlarmListener listener=getListener(ctxt);
    
    if (listener!=null) {
      if (intent.getAction()==null) {
        SharedPreferences prefs=ctxt.getSharedPreferences(WakefulIntentService.NAME, 0);

        prefs
          .edit()
          .putLong(WakefulIntentService.LAST_ALARM, System.currentTimeMillis())
          .commit();
        
        listener.sendWakefulWork(ctxt);
      }
      else {
        WakefulIntentService.scheduleAlarms(listener, ctxt, true);
      }
    }
  }
  
  @SuppressWarnings("unchecked")
  private WakefulIntentService.AlarmListener getListener(Context ctxt) {
    PackageManager pm=ctxt.getPackageManager();
    ComponentName cn=new ComponentName(ctxt, getClass());
    
    try {
      ActivityInfo ai=pm.getReceiverInfo(cn,
                                         PackageManager.GET_META_DATA);
      XmlResourceParser xpp=ai.loadXmlMetaData(pm,
                                               WAKEFUL_META_DATA);
      
      while (xpp.getEventType()!=XmlPullParser.END_DOCUMENT) {
        if (xpp.getEventType()==XmlPullParser.START_TAG) {
          if (xpp.getName().equals("WakefulIntentService")) {
            String clsName=xpp.getAttributeValue(null, "listener");
            Class<AlarmListener> cls=(Class<AlarmListener>)Class.forName(clsName);
            
            return(cls.newInstance());
          }
        }
        
        xpp.next();
      }
    }
    catch (NameNotFoundException e) {
      Log.e(getClass().getName(), "Cannot find own info???", e);
    }
    catch (XmlPullParserException e) {
      Log.e(getClass().getName(), "Malformed metadata resource XML", e);
    }
    catch (IOException e) {
      Log.e(getClass().getName(), "Could not read resource XML", e);
    }
    catch (ClassNotFoundException e) {
      Log.e(getClass().getName(), "Listener class not found", e);
    }
    catch (IllegalAccessException e) {
      Log.e(getClass().getName(), "Listener is not public or lacks public constructor", e);
    }
    catch (InstantiationException e) {
      Log.e(getClass().getName(), "Could not create instance of listener", e);
    }
    
    return(null);
  }
}