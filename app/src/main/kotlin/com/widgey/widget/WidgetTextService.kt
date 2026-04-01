package com.widgey.widget

import android.content.Intent
import android.widget.RemoteViewsService

class WidgetTextService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetTextFactory(applicationContext, intent)
    }
}
