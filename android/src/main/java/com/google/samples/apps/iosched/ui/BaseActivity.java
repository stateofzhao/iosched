/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.iosched.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.samples.apps.iosched.BuildConfig;
import com.google.samples.apps.iosched.R;
import com.google.samples.apps.iosched.injection.LoginAndAuthProvider;
import com.google.samples.apps.iosched.injection.MessagingRegistrationProvider;
import com.google.samples.apps.iosched.login.LoginAndAuth;
import com.google.samples.apps.iosched.login.LoginAndAuthListener;
import com.google.samples.apps.iosched.login.LoginStateListener;
import com.google.samples.apps.iosched.messaging.MessagingRegistration;
import com.google.samples.apps.iosched.navigation.AppNavigationViewAsDrawerImpl;
import com.google.samples.apps.iosched.navigation.NavigationModel.NavigationItemEnum;
import com.google.samples.apps.iosched.provider.ScheduleContract;
import com.google.samples.apps.iosched.service.DataBootstrapService;
import com.google.samples.apps.iosched.sync.SyncHelper;
import com.google.samples.apps.iosched.sync.account.Account;
import com.google.samples.apps.iosched.ui.widget.MultiSwipeRefreshLayout;
import com.google.samples.apps.iosched.util.AccountUtils;
import com.google.samples.apps.iosched.util.ImageLoader;
import com.google.samples.apps.iosched.util.LUtils;
import com.google.samples.apps.iosched.util.RecentTasksStyler;
import com.google.samples.apps.iosched.welcome.WelcomeActivity;

import static com.google.samples.apps.iosched.util.LogUtils.LOGD;
import static com.google.samples.apps.iosched.util.LogUtils.LOGE;
import static com.google.samples.apps.iosched.util.LogUtils.LOGW;
import static com.google.samples.apps.iosched.util.LogUtils.makeLogTag;

/*
 *
 * ContentResolver对ContentProvider的操作是单线程模式的，就是会排队来一个一个处理
 *
 * 这里先简单说一下iosched的数据加载是如何实现的。
 * 1. 大部分的数据都是来自本地的，只有少部分数据是从网络上获取的（地图覆盖物的SVG图），但是目前来看，连这个SVG图也都是本地获取的。
 *
 * 2. 数据的处理是通过 ContentProvider 来实现的。
 *
 * 3. 首先在BaseActivity中的onResume()方法中读取 工程目录中raw 数据文件，
 * 然后将读取的数据处理完成后（转换成java Bean）通过ContentProvider插入到数据库中。并且这个过程是异步的。
 *
 * 4. 第3步插入完成后，会调用ContentResolver的notify()方法来通知系统数据更新了，
 * 这样就能够让系统通知Cursor来重新加载数据（这个自动通知的过程是如何实现的呢？
 * 当通过ContentResolver调用ContentProvider 的 query()方法时，query()方法会在内部查询数据库，生成一个Cursor，
 * 并且会调用生成的Cursor的setNotificationUri()方法，这个方法内部会将Cursor的内部类SelfContentObserver
 * 注册给ContentResolver，这样当ContentProvider中数据更新时，比如ContentProvider的delete()方法，在delete()
 * 方法中会执行 ContentResolver的notifyChange()方法，这样就触发了Cursor中相关方法，
 * 导致CursorLoader重新加载数据【这个过程还没有深入研究】）。
 *
 * 5. 如何解决  当正在 初始化插入本地数据时，请求了ContentProvider中的数据，会直接显示空白，然后等本地数据插入完成后再显示出来吗？
 * 是这么做的，在onResume()中发起数据请求，然后会判断ContentResolver 是否正在 处理数据，如果正在处理就会设置
 * 刷新布局为 刷新状态（子类会在CursorLoader加载完成后来处理停止刷新状态），//todo
 *
 * 6.
 *
 *
 *
 */


/**
 * 基类Activity，封装了一些常见功能。包括，导航抽屉、登录认证、Action Bar的调整、以及其它操作。
 * 在onResume()开启数据加载；在onPause()去除了对数据加载的回调，但是并没有停止数据加载
 * <p>
 * <p>
 * A base activity that handles common functionality in the app. This includes the navigation
 * drawer, login and authentication, Action Bar tweaks, amongst others.
 */
public abstract class BaseActivity extends AppCompatActivity implements
        LoginAndAuthListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        MultiSwipeRefreshLayout.CanChildScrollUpCallback, LoginStateListener,
        AppNavigationViewAsDrawerImpl.NavigationDrawerStateListener {

    private static final String TAG = makeLogTag(BaseActivity.class);

    public static final int SWITCH_USER_RESULT = 9998;
    private static final int SELECT_GOOGLE_ACCOUNT_RESULT = 9999;

    // the LoginAndAuthHelper handles signing in to Google Play Services and OAuth
    private LoginAndAuth mLoginAndAuthProvider;

    // todo 这里需要重点关注一下，这里采用MVP的模式来实现的
    // Navigation drawer
    private AppNavigationViewAsDrawerImpl mAppNavigationViewAsDrawer;

    //封装了对Toolbar的统一处理，并没有给BaseActivity设置contentView，而是根据一个约定的id来获取Toolbar
    // Toolbar
    private Toolbar mToolbar;

    // Helper methods for L APIs
    private LUtils mLUtils;

    private static final int MAIN_CONTENT_FADEIN_DURATION = 250;

    // SwipeRefreshLayout allows the user to swipe the screen down to trigger a manual refresh
    private SwipeRefreshLayout mSwipeRefreshLayout;

    // 使用GCM来处理推送
    // Registration with GCM for notifications
    private MessagingRegistration mMessagingRegistration;

    // handle to our sync observer (that notifies us about changes in our sync state)
    private Object mSyncObserverHandle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RecentTasksStyler.styleRecentTasksEntry(this);

        // 学习这种将是否显示 说明页  的处理方式
        // Check if the EULA has been accepted; if not, show it.
        if (WelcomeActivity.shouldDisplay(this)) {
            Intent intent = new Intent(this, WelcomeActivity.class);
            startActivity(intent);
            finish();//原来谷歌也在这么干
            return;
        }

        mMessagingRegistration = MessagingRegistrationProvider.provideMessagingRegistration(this);

        //添加同步账号到Android系统中
        Account.createSyncAccount(this);

        if (savedInstanceState == null) {
            mMessagingRegistration.registerDevice();
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(this);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mLUtils = LUtils.getInstance(this);
    }

    private void trySetupSwipeRefresh() {
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        if (mSwipeRefreshLayout != null) {
            mSwipeRefreshLayout.setColorSchemeResources(
                    R.color.flat_button_text);
            mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    requestDataRefresh();
                }
            });

            if (mSwipeRefreshLayout instanceof MultiSwipeRefreshLayout) {
                MultiSwipeRefreshLayout mswrl = (MultiSwipeRefreshLayout) mSwipeRefreshLayout;
                mswrl.setCanChildScrollUpCallback(this);
            }
        }
    }

    /**
     * Returns the navigation drawer item that corresponds to this Activity. Subclasses of
     * BaseActivity override this to indicate what nav drawer item corresponds to them Return
     * NAVDRAWER_ITEM_INVALID to mean that this Activity should not have a Nav Drawer.
     */
    protected NavigationItemEnum getSelfNavDrawerItem() {
        return NavigationItemEnum.INVALID;
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        getToolbar();
        //注意，在这个方法中处理Toolbar，因为调用者很可能会在onCreate()方法中调用Toolbar相关方法，所以
        //不能放到onPostCreate()方法中处理，因为onPostCreate()会在onStart()方法后才回调
    }

    @Override
    public void onNavDrawerStateChanged(boolean isOpen, boolean isAnimating) {
        // Nothing to do
    }

    @Override
    public void onNavDrawerSlide(float offset) {
    }

    @Override
    public void onBackPressed() {
        if (mAppNavigationViewAsDrawer.isNavDrawerOpen()) {
            mAppNavigationViewAsDrawer.closeNavDrawer();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null && key.equals(BuildConfig.PREF_ATTENDEE_AT_VENUE)) {
            LOGD(TAG, "Attendee at venue preference changed, repopulating nav drawer and menu.");
            if (mAppNavigationViewAsDrawer != null) {
                mAppNavigationViewAsDrawer.updateNavigationItems();
            }
            invalidateOptionsMenu();
        }
    }

    //将对View的设置放到这个方法中比较好，这个方法执行能够表明 onCreate()方法已经彻底执行完毕了，所以可以放心的在这里获取View并对View进行设置
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mAppNavigationViewAsDrawer = new AppNavigationViewAsDrawerImpl(new ImageLoader(this), this);
        mAppNavigationViewAsDrawer.activityReady(this, this, getSelfNavDrawerItem());

        if (getSelfNavDrawerItem() != NavigationItemEnum.INVALID) {
            setToolbarForNavigation();
        }

        trySetupSwipeRefresh();

        View mainContent = findViewById(R.id.main_content);
        if (mainContent != null) {
            mainContent.setAlpha(0);
            mainContent.animate().alpha(1).setDuration(MAIN_CONTENT_FADEIN_DURATION);
        } else {
            LOGW(TAG, "No view with ID main_content to fade in.");
        }
    }


    @Override
    public void onAccountChangeRequested() {
        // override if you want to be notified when another account has been selected account has
        // changed
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_refresh:
                requestDataRefresh();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 让android framework 执行数据同步，不再是从本地数据库中获取数据 */
    protected void requestDataRefresh() {
        android.accounts.Account activeAccount = AccountUtils.getActiveAccount(this);
        ContentResolver contentResolver = getContentResolver();
        if (contentResolver.isSyncActive(activeAccount, ScheduleContract.CONTENT_AUTHORITY)) {
            LOGD(TAG, "Ignoring manual sync request because a sync is already in progress.");
            return;
        }
        LOGD(TAG, "Requesting manual data refresh.");
        SyncHelper.requestManualSync();
    }

    /**
     * This utility method handles Up navigation intents by searching for a parent activity and
     * navigating there if defined. When using this for an activity make sure to define both the
     * native parentActivity as well as the AppCompat one when supporting API levels less than 16.
     * when the activity has a single parent activity. If the activity doesn't have a single parent
     * activity then don't define one and this method will use back button functionality. If "Up"
     * functionality is still desired for activities without parents then use {@code
     * syntheticParentActivity} to define one dynamically.
     * <p/>
     * Note: Up navigation intents are represented by a back arrow in the top left of the Toolbar in
     * Material Design guidelines.
     *
     * @param currentActivity         Activity in use when navigate Up action occurred.
     * @param syntheticParentActivity Parent activity to use when one is not already configured.
     */
    public static void navigateUpOrBack(Activity currentActivity,
            Class<? extends Activity> syntheticParentActivity) {
        // Retrieve parent activity from AndroidManifest.
        Intent intent = NavUtils.getParentActivityIntent(currentActivity);

        // Synthesize the parent activity when a natural one doesn't exist.
        if (intent == null && syntheticParentActivity != null) {
            try {
                intent = NavUtils.getParentActivityIntent(currentActivity, syntheticParentActivity);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        if (intent == null) {
            // No parent defined in manifest. This indicates the activity may be used by
            // in multiple flows throughout the app and doesn't have a strict parent. In
            // this case the navigation up button should act in the same manner as the
            // back button. This will result in users being forwarded back to other
            // applications if currentActivity was invoked from another application.
            currentActivity.onBackPressed();
        } else {
            if (NavUtils.shouldUpRecreateTask(currentActivity, intent)) {
                // Need to synthesize a backstack since currentActivity was probably invoked by a
                // different app. The preserves the "Up" functionality within the app according to
                // the activity hierarchy defined in AndroidManifest.xml via parentActivity
                // attributes.
                TaskStackBuilder builder = TaskStackBuilder.create(currentActivity);
                builder.addNextIntentWithParentStack(intent);
                builder.startActivities();
            } else {
                // Navigate normally to the manifest defined "Up" activity.
                NavUtils.navigateUpTo(currentActivity, intent);
            }
        }
    }

    @Override
    public void onSignInOrCreateAccount() {
        //Get list of accounts on device.
        android.accounts.AccountManager am = android.accounts.AccountManager.get(BaseActivity.this);
        android.accounts.Account[] accountArray =
                am.getAccountsByType(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
        if (accountArray.length == 0) {
            //Send the user to the "Add Account" page.
            Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
            intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES,
                    new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE});
            startActivity(intent);
        } else {
            //Try to log the user in with the first account on the device.
            startLoginProcess();
            mAppNavigationViewAsDrawer.closeNavDrawer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 初始化本地数据到数据库中，是否初始化完成是放到了 SharedPreferences中来标记的
        // Perform one-time bootstrap setup, if needed
        DataBootstrapService.startDataBootstrapIfNecessary(this);

        // Check to ensure a Google Account is active for the app. Placing the check here ensures
        // it is run again in the case where a Google Account wasn't present on the device and a
        // picker had to be started.
        if (!AccountUtils.enforceActiveGoogleAccount(this, SELECT_GOOGLE_ACCOUNT_RESULT)) {
            LOGD(TAG, "EnforceActiveGoogleAccount returned false");
            return;
        }

        // 监听数据的同步状态，如果是同步中或者在同步队列中，那么就会改变刷新View的显示状态。
        // 注意，这里并没有做开启数据请求的处理，仅仅是维护了一个刷新View的显示状态。
        // Watch for sync state changes
        mSyncStatusObserver.onStatusChanged(0);
        final int mask = ContentResolver.SYNC_OBSERVER_TYPE_PENDING |
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE;
        mSyncObserverHandle = ContentResolver.addStatusChangeListener(mask, mSyncStatusObserver);

        startLoginProcess();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSyncObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
            mSyncObserverHandle = null;
        }

        if (mLoginAndAuthProvider != null) {
            mLoginAndAuthProvider.stop();
        }
    }

    /**
     * Converts an intent into a {@link Bundle} suitable for use as fragment arguments.
     */
    public static Bundle intentToFragmentArguments(Intent intent) {
        Bundle arguments = new Bundle();
        if (intent == null) {
            return arguments;
        }

        final Uri data = intent.getData();
        if (data != null) {
            arguments.putParcelable("_uri", data);
        }

        final Bundle extras = intent.getExtras();
        if (extras != null) {
            arguments.putAll(intent.getExtras());
        }

        return arguments;
    }

    @Override
    public void onStartLoginProcessRequested() {
        startLoginProcess();
    }

    private void startLoginProcess() {
        LOGD(TAG, "Starting login process.");
        if (!AccountUtils.hasActiveAccount(this)) {
            LOGD(TAG, "No active account, attempting to pick a default.");
            String defaultAccount = AccountUtils.getActiveAccountName(this);
            if (defaultAccount == null) {
                LOGE(TAG, "Failed to pick default account (no accounts). Failing.");
                //complainMustHaveGoogleAccount();
                return;
            }
            LOGD(TAG, "Default to: " + defaultAccount);
            AccountUtils.setActiveAccount(this, defaultAccount);
        }

        if (!AccountUtils.hasActiveAccount(this)) {
            LOGD(TAG, "Can't proceed with login -- no account chosen.");
            return;
        } else {
            LOGD(TAG, "Chosen account: " + AccountUtils.getActiveAccountName(this));
        }

        String accountName = AccountUtils.getActiveAccountName(this);
        LOGD(TAG, "Chosen account: " + AccountUtils.getActiveAccountName(this));

        if (mLoginAndAuthProvider != null && mLoginAndAuthProvider.getAccountName()
                                                                  .equals(accountName)) {
            LOGD(TAG, "Helper already set up; simply starting it.");
            mLoginAndAuthProvider.start();
            return;
        }

        LOGD(TAG, "Starting login process with account " + accountName);

        if (mLoginAndAuthProvider != null) {
            LOGD(TAG, "Tearing down old Helper, was " + mLoginAndAuthProvider.getAccountName());
            if (mLoginAndAuthProvider.isStarted()) {
                LOGD(TAG, "Unregister device from GCM");
                mMessagingRegistration.unregisterDevice(mLoginAndAuthProvider.getAccountName());
                LOGD(TAG, "Stopping old Helper");
                mLoginAndAuthProvider.stop();
            }
            mLoginAndAuthProvider = null;
        }

        LOGD(TAG, "Creating and starting new Helper with account: " + accountName);
        mLoginAndAuthProvider =
                LoginAndAuthProvider.provideLoginAndAuth(this, this, accountName);
        mLoginAndAuthProvider.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SELECT_GOOGLE_ACCOUNT_RESULT) {
            // Handle the select {@code startActivityForResult} from
            // {@code enforceActiveGoogleAccount()} when a Google Account wasn't present on the
            // device.
            if (resultCode == RESULT_OK) {
                // Set selected GoogleAccount as active.
                String accountName =
                        data.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME);
                AccountUtils.setActiveAccount(this, accountName);
                onAuthSuccess(accountName, true);
            } else {
                LOGW(TAG, "A Google Account is required to use this application.");
                // This application requires a Google Account to be selected.
                finish();
            }
            return;
        } else if (requestCode == SWITCH_USER_RESULT) {
            // Handle account change notifications after {@link SwitchUserActivity} has been invoked
            // (typically by {@link AppNavigationViewAsDrawerImpl}).
            if (resultCode == RESULT_OK) {
                onAccountChangeRequested();
                onStartLoginProcessRequested();
            }
        }

        if (mLoginAndAuthProvider == null || !mLoginAndAuthProvider.onActivityResult(requestCode,
                resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onPlusInfoLoaded(String accountName) {
        mAppNavigationViewAsDrawer.updateNavigationItems();
    }

    /**
     * Called when authentication succeeds. This may either happen because the user just
     * authenticated for the first time (and went through the sign in flow), or because it's a
     * returning user.
     *
     * @param accountName        name of the account that just authenticated successfully.
     * @param newlyAuthenticated If true, this user just authenticated for the first time. If false,
     *                           it's a returning user.
     */
    @Override
    public void onAuthSuccess(String accountName, boolean newlyAuthenticated) {
        android.accounts.Account account =
                new android.accounts.Account(accountName, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
        LOGD(TAG, "onAuthSuccess, account " + accountName + ", newlyAuthenticated="
                + newlyAuthenticated);

        refreshAccountDependantData();

        if (newlyAuthenticated) {
            LOGD(TAG, "Enabling auto sync on content provider for account " + accountName);
            SyncHelper.updateSyncInterval(this);
            SyncHelper.requestManualSync();
        }

        mAppNavigationViewAsDrawer.updateNavigationItems();
        mMessagingRegistration.registerDevice();
    }

    @Override
    public void onAuthFailure(String accountName) {
        LOGD(TAG, "Auth failed for account " + accountName);
        refreshAccountDependantData();
        mAppNavigationViewAsDrawer.updateNavigationItems();
    }

    /** 更新所有依赖于账户的数据 */
    protected void refreshAccountDependantData() {
        // Force local data refresh for data that depends on the logged user:
        LOGD(TAG, "Refreshing User Data");
        getContentResolver().notifyChange(ScheduleContract.MySchedule.CONTENT_URI, null, false);
        getContentResolver().notifyChange(ScheduleContract.MyViewedVideos.CONTENT_URI, null, false);
        getContentResolver().notifyChange(
                ScheduleContract.MyFeedbackSubmitted.CONTENT_URI, null, false);
    }

    protected void retryAuth() {
        mLoginAndAuthProvider.retryAuthByUserRequest();
    }

    public Toolbar getToolbar() {
        if (mToolbar == null) {
            mToolbar = (Toolbar) findViewById(R.id.toolbar);
            if (mToolbar != null) {
                mToolbar.setNavigationContentDescription(getResources().getString(R.string
                        .navdrawer_description_a11y));
                setSupportActionBar(mToolbar);
            }
        }
        return mToolbar;
    }

    private void setToolbarForNavigation() {
        if (mToolbar != null) {
            mToolbar.setNavigationIcon(R.drawable.ic_hamburger);
            mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mAppNavigationViewAsDrawer.showNavigation();
                }
            });
        }
    }

    /**
     * @param clickListener The {@link android.view.View.OnClickListener} for the navigation icon of
     *                      the toolbar.
     */
    protected void setToolbarAsUp(View.OnClickListener clickListener) {
        // Initialise the toolbar
        getToolbar();

        mToolbar.setNavigationIcon(R.drawable.ic_up);
        mToolbar.setNavigationContentDescription(R.string.close_and_go_back);
        mToolbar.setNavigationOnClickListener(clickListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mMessagingRegistration != null) {
            mMessagingRegistration.destroy();
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.unregisterOnSharedPreferenceChangeListener(this);
    }

    private SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        @Override
        public void onStatusChanged(int which) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String accountName = AccountUtils.getActiveAccountName(BaseActivity.this);
                    if (TextUtils.isEmpty(accountName)) {
                        onRefreshingStateChanged(false);
                        return;
                    }

                    android.accounts.Account account = new android.accounts.Account(
                            accountName, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
                    //如果正在执行同步，那么就让当前页面的刷新布局置为 下拉刷新 状态
                    boolean syncActive = ContentResolver.isSyncActive(
                            account, ScheduleContract.CONTENT_AUTHORITY);
                    onRefreshingStateChanged(syncActive);
                }
            });
        }
    };

    /** 仅仅用来改变刷新View的显示状态，不会触发数据请求 */
    protected void onRefreshingStateChanged(boolean refreshing) {
        if (mSwipeRefreshLayout != null) {
            //调用这个方法不会触发给 SwipeRefreshLayout 注册的刷新监听方法
            mSwipeRefreshLayout.setRefreshing(refreshing);
        }
    }

    public LUtils getLUtils() {
        return mLUtils;
    }

    @Override
    public boolean canSwipeRefreshChildScrollUp() {
        return false;
    }

    /**
     * Configure this Activity as a floating window, with the given {@code width}, {@code height}
     * and {@code alpha}, and dimming the background with the given {@code dim} value.
     */
    protected void setupFloatingWindow(int width, int height, int alpha, float dim) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = getResources().getDimensionPixelSize(width);
        params.height = getResources().getDimensionPixelSize(height);
        params.alpha = alpha;
        params.dimAmount = dim;
        params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        getWindow().setAttributes(params);
    }

    /**
     * Returns true if the theme sets the {@code R.attr.isFloatingWindow} flag to true.
     */
    protected boolean shouldBeFloatingWindow() {
        Resources.Theme theme = getTheme();
        TypedValue floatingWindowFlag = new TypedValue();

        // Check isFloatingWindow flag is defined in theme.
        if (theme == null || !theme
                .resolveAttribute(R.attr.isFloatingWindow, floatingWindowFlag, true)) {
            return false;
        }

        return (floatingWindowFlag.data != 0);
    }

}
