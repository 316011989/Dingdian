/*
 * Copyright 2017 jiajunhui<junhui_jia@163.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.kk.taurus.playerbase.config;

import android.app.Application;
import com.kk.taurus.playerbase.lebo.LeCast;
import com.kk.taurus.playerbase.utils.VideoUtil;

public class PlayerLibrary {

    public static String playKey = "saIZXc4yMvq0Iz56";
    public static int isLocal = 0;

    public static void init(Application application) {
        AppContextAttach.attach(application);
        LeCast.getInstance().initLeCast(application);
        VideoUtil.init(application);
    }
}
