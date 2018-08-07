/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.osmdroid.api.IMapController;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.LocationActivityAdapter;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Cells.LocationLoadingCell;
import org.telegram.ui.Cells.LocationPoweredCell;
import org.telegram.ui.Cells.SendLocationCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MapPlaceholderDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class LocationActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public class LiveLocation {
        public int id;
        public TLRPC.Message object;
        public TLRPC.User user;
        public TLRPC.Chat chat;
        public Marker marker;
    }

    private MapView mapView;
    private FrameLayout mapViewClip;
    private LocationActivityAdapter adapter;
    private RecyclerListView listView;
    private ImageView markerImageView;
    private ImageView markerXImageView;
    private ImageView locationButton;
    private ImageView routeButton;
    private LinearLayoutManager layoutManager;
    private AvatarDrawable avatarDrawable;
    private TextView attributionOverlay;
    private ActionBarMenuItem otherItem;

    private boolean checkGpsEnabled = true;

    private boolean isFirstLocation = true;
    private long dialogId;

    private boolean firstFocus = true;

    private Runnable updateRunnable;

    private ArrayList<LiveLocation> markers = new ArrayList<>();
    private HashMap<Integer, LiveLocation> markersMap = new HashMap<>();

    private AnimatorSet animatorSet;

    private boolean checkPermission = true;

    private boolean mapsInitialized;

    private Location myLocation;
    private Location userLocation;
    private int markerTop;

    private MessageObject messageObject;
    private boolean userLocationMoved = false;
    private boolean firstWas = false;
    private LocationActivityDelegate delegate;

    private int liveLocationType;

    private int overScrollHeight = AndroidUtilities.displaySize.x - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(66);

    private final static int share = 1;
    /* TFOSS: TileSources
    private final static int map_list_menu_map = 2;
    private final static int map_list_menu_satellite = 3;
    private final static int map_list_menu_hybrid = 4;
    */

    private MyLocationNewOverlay myLocationOverlay;

    public interface LocationActivityDelegate {
        void didSelectLocation(TLRPC.MessageMedia location, int live);
    }

    public LocationActivity(int liveLocation) {
        super();
        liveLocationType = liveLocation;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        swipeBackEnabled = false;
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.locationPermissionGranted);
        if (messageObject != null && messageObject.isLiveLocation()) {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.didReceivedNewMessages);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesDeleted);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.replaceMessagesObjects);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceivedNewMessages);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.replaceMessagesObjects);
        if (adapter != null) {
            adapter.destroy();
        }
        if (updateRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(updateRunnable);
            updateRunnable = null;
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setAddToContainer(false);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } /* TFOSS: TileSources https://github.com/osmdroid/osmdroid/wiki/Map-Sources
                else if (id == map_list_menu_map) {
                    if (googleMap != null) {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    }
                } else if (id == map_list_menu_satellite) {
                    if (googleMap != null) {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                    }
                } else if (id == map_list_menu_hybrid) {
                    if (googleMap != null) {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    }
                }*/ else if (id == share) {
                    try {
                        double lat = messageObject.messageOwner.media.geo.lat;
                        double lon = messageObject.messageOwner.media.geo._long;
                        getParentActivity().startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        if (messageObject != null) {
            if (messageObject.isLiveLocation()) {
                actionBar.setTitle(LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation));
            } else {
                if (messageObject.messageOwner.media.title != null && messageObject.messageOwner.media.title.length() > 0) {
                    actionBar.setTitle(LocaleController.getString("SharedPlace", R.string.SharedPlace));
                } else {
                    actionBar.setTitle(LocaleController.getString("ChatLocation", R.string.ChatLocation));
                }
                menu.addItem(share, R.drawable.share);
            }
        } else {
            actionBar.setTitle(LocaleController.getString("ShareLocation", R.string.ShareLocation));
        }
        /* TFOSS: TileSources https://github.com/osmdroid/osmdroid/wiki/Map-Sources
        otherItem = menu.addItem(0, R.drawable.ic_ab_other);
        otherItem.addSubItem(map_list_menu_map, LocaleController.getString("Map", R.string.Map));
        otherItem.addSubItem(map_list_menu_satellite, LocaleController.getString("Satellite", R.string.Satellite));
        otherItem.addSubItem(map_list_menu_hybrid, LocaleController.getString("Hybrid", R.string.Hybrid));
        */
        fragmentView = new FrameLayout(context) {
            private boolean first = true;

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                if (changed) {
                    fixLayoutInternal(first);
                    first = false;
                }
            }
        };
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        locationButton = new ImageView(context);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_profile_actionBackground), Theme.getColor(Theme.key_profile_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        locationButton.setBackgroundDrawable(drawable);
        locationButton.setImageResource(R.drawable.myloc_on);
        locationButton.setScaleType(ImageView.ScaleType.CENTER);
        locationButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_actionIcon), PorterDuff.Mode.MULTIPLY));
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(locationButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(locationButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            locationButton.setStateListAnimator(animator);
            locationButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }

        if (messageObject != null) {
            userLocation = new Location("network");
            userLocation.setLatitude(messageObject.messageOwner.media.geo.lat);
            userLocation.setLongitude(messageObject.messageOwner.media.geo._long);
        }

        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (mapView != null && getParentActivity() != null) {
                    mapView.setPadding(0, 0, 0, AndroidUtilities.dp(10));
                    onMapInit();
                    mapsInitialized = true;
                }
            }
        });

        attributionOverlay = new TextView(context);
        attributionOverlay.setText(Html.fromHtml("© <a href=\"http://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors"));
        attributionOverlay.setShadowLayer(1,-1,-1, Color.WHITE);
        attributionOverlay.setLinksClickable(true);
        attributionOverlay.setMovementMethod(LinkMovementMethod.getInstance());

        mapViewClip = new FrameLayout(context);

        mapViewClip.addView(attributionOverlay, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, LocaleController.isRTL ? 0 : 4, 0, LocaleController.isRTL ? 4 : 0, 5));

        mapViewClip.setBackgroundDrawable(new MapPlaceholderDrawable());
        if (adapter != null) {
            adapter.destroy();
        }

        listView = new RecyclerListView(context);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setAdapter(adapter = new LocationActivityAdapter(context, liveLocationType, dialogId));
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (adapter.getItemCount() == 0) {
                    return;
                }
                int position = layoutManager.findFirstVisibleItemPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                updateClipView(position);
            }
        });
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position == 1 && messageObject != null && !messageObject.isLiveLocation()) {
                    if (mapView != null) {
                        final IMapController controller = mapView.getController();
                        controller.setZoom(mapView.getMaxZoomLevel() - 1);
                        controller.animateTo(new GeoPoint(messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long));
                    }
                } else if (position == 1 && liveLocationType != 2) {
                    if (delegate != null && userLocation != null) {
                        TLRPC.TL_messageMediaGeo location = new TLRPC.TL_messageMediaGeo();
                        location.geo = new TLRPC.TL_geoPoint();
                        location.geo.lat = userLocation.getLatitude();
                        location.geo._long = userLocation.getLongitude();
                        delegate.didSelectLocation(location, liveLocationType);
                    }
                    finishFragment();
                } else if (position == 2 && liveLocationType == 1 || position == 1 && liveLocationType == 2 || position == 3 && liveLocationType == 3) {
                    if (LocationController.getInstance().isSharingLocation(dialogId)) {
                        LocationController.getInstance().removeSharingLocation(dialogId);
                        finishFragment();
                    } else {
                        if (delegate == null || getParentActivity() == null) {
                            return;
                        }
                        if (myLocation != null) {
                            TLRPC.User user = null;
                            if ((int) dialogId > 0) {
                                user = MessagesController.getInstance().getUser((int) dialogId);
                            }
                            showDialog(AlertsCreator.createLocationUpdateDialog(getParentActivity(), user, new MessagesStorage.IntCallback() {
                                @Override
                                public void run(int param) {
                                    TLRPC.TL_messageMediaGeoLive location = new TLRPC.TL_messageMediaGeoLive();
                                    location.geo = new TLRPC.TL_geoPoint();
                                    location.geo.lat = myLocation.getLatitude();
                                    location.geo._long = myLocation.getLongitude();
                                    location.period = param;
                                    delegate.didSelectLocation(location, liveLocationType);
                                    finishFragment();
                                }
                            }));
                        }
                    }
                } else {
                    Object object = adapter.getItem(position);
                    if (object instanceof TLRPC.TL_messageMediaVenue) {
                        if (object != null && delegate != null) {
                            delegate.didSelectLocation((TLRPC.TL_messageMediaVenue) object, liveLocationType);
                        }
                        finishFragment();
                    } else if (object instanceof LiveLocation) {
                        final IMapController controller = mapView.getController();
                        controller.setZoom(mapView.getMaxZoomLevel() - 1);
                        controller.animateTo(((LiveLocation) object).marker.getPosition());
                    }
                }
            }
        });
        adapter.setOverScrollHeight(overScrollHeight);

        frameLayout.addView(mapViewClip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        mapView = new MapView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (messageObject == null) {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                        if (animatorSet != null) {
                            animatorSet.cancel();
                        }
                        animatorSet = new AnimatorSet();
                        animatorSet.setDuration(200);
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(markerImageView, "translationY", markerTop + -AndroidUtilities.dp(10)),
                                ObjectAnimator.ofFloat(markerXImageView, "alpha", 1.0f));
                        animatorSet.start();
                    } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                        if (animatorSet != null) {
                            animatorSet.cancel();
                        }
                        animatorSet = new AnimatorSet();
                        animatorSet.setDuration(200);
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(markerImageView, "translationY", markerTop),
                                ObjectAnimator.ofFloat(markerXImageView, "alpha", 0.0f));
                        animatorSet.start();
                    }
                    if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                        if (!userLocationMoved) {
                            AnimatorSet animatorSet = new AnimatorSet();
                            animatorSet.setDuration(200);
                            animatorSet.play(ObjectAnimator.ofFloat(locationButton, "alpha", 1.0f));
                            animatorSet.start();
                            userLocationMoved = true;
                        }
                        if (mapView != null && userLocation != null) {
                            userLocation.setLatitude(mapView.getMapCenter().getLatitude());
                            userLocation.setLongitude(mapView.getMapCenter().getLongitude());
                        }
                        adapter.setCustomLocation(userLocation);
                    }
                }
                return super.onTouchEvent(ev);
            }
        };

        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (mapView != null && getParentActivity() != null) {
                    mapView.setPadding(0, 0, 0, AndroidUtilities.dp(10));
                    onMapInit();
                    mapsInitialized = true;
                }
            }
        });

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        mapViewClip.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.BOTTOM));

        if (messageObject == null) {
            markerImageView = new ImageView(context);
            markerImageView.setImageResource(R.drawable.map_pin);
            mapViewClip.addView(markerImageView, LayoutHelper.createFrame(24, 42, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

            markerXImageView = new ImageView(context);
            markerXImageView.setAlpha(0.0f);
            markerXImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_location_markerX), PorterDuff.Mode.MULTIPLY));
            markerXImageView.setImageResource(R.drawable.place_x);
            mapViewClip.addView(markerXImageView, LayoutHelper.createFrame(14, 14, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        } else if (!messageObject.isLiveLocation()) {
            routeButton = new ImageView(context);
            drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
            if (Build.VERSION.SDK_INT < 21) {
                Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
                shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
                CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
                combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                drawable = combinedDrawable;
            }
            routeButton.setBackgroundDrawable(drawable);
            routeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
            routeButton.setImageResource(R.drawable.navigate);
            routeButton.setScaleType(ImageView.ScaleType.CENTER);
            if (Build.VERSION.SDK_INT >= 21) {
                StateListAnimator animator = new StateListAnimator();
                animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(routeButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
                animator.addState(new int[]{}, ObjectAnimator.ofFloat(routeButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
                routeButton.setStateListAnimator(animator);
                routeButton.setOutlineProvider(new ViewOutlineProvider() {
                    @SuppressLint("NewApi")
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                    }
                });
            }
            frameLayout.addView(routeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 37));
            routeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        Activity activity = getParentActivity();
                        if (activity != null) {
                            if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                showPermissionAlert(true);
                                return;
                            }
                        }
                    }
                    if (myLocation != null) {
                        try {
                            Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "http://maps.google.com/maps?saddr=%f,%f&daddr=%f,%f", myLocation.getLatitude(), myLocation.getLongitude(), messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long)));
                            getParentActivity().startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            });

            adapter.setMessageObject(messageObject);
        }

        if (messageObject != null && !messageObject.isLiveLocation()) {
            mapViewClip.addView(locationButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 43));
        } else {
            mapViewClip.addView(locationButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14));
        }
        locationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 23) {
                    Activity activity = getParentActivity();
                    if (activity != null) {
                        if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            showPermissionAlert(false);
                            return;
                        }
                    }
                }
                if (messageObject != null) {
                    if (myLocation != null && mapView != null) {
                        final IMapController controller = mapView.getController();
                        controller.setZoom(mapView.getMaxZoomLevel() - 1);
                        controller.animateTo(new GeoPoint(myLocation.getLatitude(), myLocation.getLongitude()));
                    }
                } else {
                    if (myLocation != null && mapView != null) {
                        AnimatorSet animatorSet = new AnimatorSet();
                        animatorSet.setDuration(200);
                        animatorSet.play(ObjectAnimator.ofFloat(locationButton, "alpha", 0.0f));
                        animatorSet.start();
                        adapter.setCustomLocation(null);
                        userLocationMoved = false;

                        final IMapController controller = mapView.getController();
                        controller.animateTo(new GeoPoint(myLocation.getLatitude(), myLocation.getLongitude()));
                    }
                }
            }
        });
        if (messageObject == null) {
            locationButton.setAlpha(0.0f);
        }

        frameLayout.addView(actionBar);

        return fragmentView;
    }

    private Bitmap createUserBitmap(LiveLocation liveLocation) {
        Bitmap result = null;
        try {
            TLRPC.FileLocation photo = null;
            if (liveLocation.user != null && liveLocation.user.photo != null) {
                photo = liveLocation.user.photo.photo_small;
            } else if (liveLocation.chat != null && liveLocation.chat.photo != null) {
                photo = liveLocation.chat.photo.photo_small;
            }
            result = Bitmap.createBitmap(AndroidUtilities.dp(62), AndroidUtilities.dp(76), Bitmap.Config.ARGB_8888);
            result.eraseColor(Color.TRANSPARENT);
            Canvas canvas = new Canvas(result);
            Drawable drawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.livepin);
            drawable.setBounds(0, 0, AndroidUtilities.dp(62), AndroidUtilities.dp(76));
            drawable.draw(canvas);

            Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            RectF bitmapRect = new RectF();
            canvas.save();
            if (photo != null) {
                File path = FileLoader.getPathToAttach(photo, true);
                Bitmap bitmap = BitmapFactory.decodeFile(path.toString());
                if (bitmap != null) {
                    BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    Matrix matrix = new Matrix();
                    float scale = AndroidUtilities.dp(52) / (float) bitmap.getWidth();
                    matrix.postTranslate(AndroidUtilities.dp(5), AndroidUtilities.dp(5));
                    matrix.postScale(scale, scale);
                    roundPaint.setShader(shader);
                    shader.setLocalMatrix(matrix);
                    bitmapRect.set(AndroidUtilities.dp(5), AndroidUtilities.dp(5), AndroidUtilities.dp(52 + 5), AndroidUtilities.dp(52 + 5));
                    canvas.drawRoundRect(bitmapRect, AndroidUtilities.dp(26), AndroidUtilities.dp(26), roundPaint);
                }
            } else {
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                if (liveLocation.user != null) {
                    avatarDrawable.setInfo(liveLocation.user);
                } else if (liveLocation.chat != null) {
                    avatarDrawable.setInfo(liveLocation.chat);
                }
                canvas.translate(AndroidUtilities.dp(5), AndroidUtilities.dp(5));
                avatarDrawable.setBounds(0, 0, AndroidUtilities.dp(52.2f), AndroidUtilities.dp(52.2f));
                avatarDrawable.draw(canvas);
            }
            canvas.restore();
            try {
                canvas.setBitmap(null);
            } catch (Exception e) {
                //don't promt, this will crash on 2.x
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return result;
    }

    private int getMessageId(TLRPC.Message message) {
        if (message.from_id != 0) {
            return message.from_id;
        } else {
            return (int) MessageObject.getDialogId(message);
        }
    }

    private LiveLocation addUserMarker(TLRPC.Message message) {
        LiveLocation liveLocation;
        GeoPoint latLng = new GeoPoint(message.media.geo.lat, message.media.geo._long);
        if ((liveLocation = markersMap.get(message.from_id)) == null) {
            liveLocation = new LiveLocation();
            liveLocation.object = message;
            if (liveLocation.object.from_id != 0) {
                liveLocation.user = MessagesController.getInstance().getUser(liveLocation.object.from_id);
                liveLocation.id = liveLocation.object.from_id;
            } else {
                int did = (int) MessageObject.getDialogId(message);
                if (did > 0) {
                    liveLocation.user = MessagesController.getInstance().getUser(did);
                    liveLocation.id = did;
                } else {
                    liveLocation.chat = MessagesController.getInstance().getChat(-did);
                    liveLocation.id = -did;
                }
            }

            try {
                Marker marker = new Marker(mapView);
                marker.setPosition(latLng);
                Bitmap bitmap = createUserBitmap(liveLocation);
                if (bitmap != null) {
                    marker.setIcon(new BitmapDrawable(getParentActivity().getResources(), bitmap));
                    marker.setAnchor(0.5f, 0.907f);
                    mapView.getOverlays().add(marker);
                    liveLocation.marker = marker;

                    markers.add(liveLocation);
                    markersMap.put(liveLocation.id, liveLocation);
                    LocationController.SharingLocationInfo myInfo = LocationController.getInstance().getSharingLocationInfo(dialogId);
                    if (liveLocation.id == UserConfig.getClientUserId() && myInfo != null && liveLocation.object.id == myInfo.mid && myLocation != null) {
                        liveLocation.marker.setPosition(new GeoPoint(myLocation.getLatitude(), myLocation.getLongitude()));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            liveLocation.object = message;
            liveLocation.marker.setPosition(latLng);
        }
        return liveLocation;
    }

    private void onMapInit() {
        if (mapView == null) {
            return;
        }
        final IMapController controller = mapView.getController();
        //controller.setZoom(mapView.getMaxZoomLevel() - 1);
        controller.setZoom(4);

        if (messageObject != null) {
            if (messageObject.isLiveLocation()) {
                LiveLocation liveLocation = addUserMarker(messageObject.messageOwner);
                if (!getRecentLocations()) {
                    controller.animateTo(liveLocation.marker.getPosition());

                }
            } else {

                GeoPoint latLng = new GeoPoint(userLocation.getLatitude(), userLocation.getLongitude());
                Marker marker = new Marker(mapView);
                marker.setPosition(latLng);
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                if (Build.VERSION.SDK_INT >= 21) {
                    marker.setIcon(getParentActivity().getDrawable(R.drawable.map_pin));
                } else {
                    marker.setIcon(getParentActivity().getResources().getDrawable(R.drawable.map_pin));
                }
                mapView.getOverlays().add(marker);
                controller.animateTo(latLng);

                firstFocus = false;
                getRecentLocations();
            }
        } else {
            userLocation = new Location("network");
            userLocation.setLatitude(20.659322);
            userLocation.setLongitude(-11.406250);
        }

        mapView.setMultiTouchControls(true);

        GpsMyLocationProvider imlp = new GpsMyLocationProvider(getParentActivity());
        imlp.setLocationUpdateMinDistance(10);
        imlp.setLocationUpdateMinTime(10000);
        imlp.addLocationSource(LocationManager.NETWORK_PROVIDER);
        myLocationOverlay = new MyLocationNewOverlay(imlp, mapView) {
            @Override
            public void onLocationChanged(final Location location, IMyLocationProvider source) {
                super.onLocationChanged(location, source);
                if (location != null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            positionMarker(location);
                            LocationController.getInstance().setGoogleMapLocation(location, isFirstLocation);
                            isFirstLocation = false;
                        }
                    });
                }
            }
        };
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.setDrawAccuracyEnabled(true);

        myLocationOverlay.runOnFirstFix(new Runnable() {
            public void run() {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        positionMarker(myLocationOverlay.getLastFix());
                        LocationController.getInstance().setGoogleMapLocation(myLocationOverlay.getLastFix(), isFirstLocation);
                        isFirstLocation = false;
                    }
                });
            }
        });
        mapView.getOverlays().add(myLocationOverlay);
        positionMarker(myLocation = getLastLocation());

        attributionOverlay.bringToFront();

        if (checkGpsEnabled && getParentActivity() != null) {
            checkGpsEnabled = false;
            try {
                LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
                if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("GpsDisabledAlert", R.string.GpsDisabledAlert));
                    builder.setPositiveButton(LocaleController.getString("ConnectingToProxyEnable", R.string.ConnectingToProxyEnable), new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int id) {
                            if (getParentActivity() == null) {
                                return;
                            }
                            try {
                                getParentActivity().startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                            } catch (Exception ignore) {

                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void showPermissionAlert(boolean byButton) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        if (byButton) {
            builder.setMessage(LocaleController.getString("PermissionNoLocationPosition", R.string.PermissionNoLocationPosition));
        } else {
            builder.setMessage(LocaleController.getString("PermissionNoLocation", R.string.PermissionNoLocation));
        }
        builder.setNegativeButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), new DialogInterface.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.GINGERBREAD)
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (getParentActivity() == null) {
                    return;
                }
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                    getParentActivity().startActivity(intent);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            try {
                if (mapView.getParent() instanceof ViewGroup) {
                    ViewGroup viewGroup = (ViewGroup) mapView.getParent();
                    viewGroup.removeView(mapView);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (mapViewClip != null) {
                mapViewClip.addView(mapView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + AndroidUtilities.dp(10), Gravity.TOP | Gravity.LEFT));
                updateClipView(layoutManager.findFirstVisibleItemPosition());
            } else if (fragmentView != null) {
                ((FrameLayout) fragmentView).addView(mapView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            }
        }
    }

    private void updateClipView(int firstVisibleItem) {
        if (firstVisibleItem == RecyclerView.NO_POSITION) {
            return;
        }
        int height = 0;
        int top = 0;
        View child = listView.getChildAt(0);
        if (child != null) {
            if (firstVisibleItem == 0) {
                top = child.getTop();
                height = overScrollHeight + (top < 0 ? top : 0);
            }
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mapViewClip.getLayoutParams();
            if (layoutParams != null) {

                mapViewClip.setTranslationY(Math.min(0, top));
                mapView.setTranslationY(Math.max(0, -top / 2));
                if (markerImageView != null) {
                    markerImageView.setTranslationY(markerTop = -top - AndroidUtilities.dp(42) + height / 2);
                    markerXImageView.setTranslationY(-top - AndroidUtilities.dp(7) + height / 2);
                }
                if (routeButton != null) {
                    routeButton.setTranslationY(top);
                }
                layoutParams = (FrameLayout.LayoutParams) mapView.getLayoutParams();
                if (layoutParams != null && layoutParams.height != overScrollHeight + AndroidUtilities.dp(10)) {
                    layoutParams.height = overScrollHeight + AndroidUtilities.dp(10);
                    if (mapView != null) {
                        mapView.setPadding(0, 0, AndroidUtilities.dp(70), AndroidUtilities.dp(10));
                    }
                    mapView.setLayoutParams(layoutParams);
                }
            }
        }
    }

    private void fixLayoutInternal(final boolean resume) {
        if (listView != null) {
            int height = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
            int viewHeight = fragmentView.getMeasuredHeight();
            if (viewHeight == 0) {
                return;
            }
            overScrollHeight = viewHeight - AndroidUtilities.dp(66) - height;

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.topMargin = height;
            listView.setLayoutParams(layoutParams);
            layoutParams = (FrameLayout.LayoutParams) mapViewClip.getLayoutParams();
            layoutParams.topMargin = height;
            layoutParams.height = overScrollHeight;
            mapViewClip.setLayoutParams(layoutParams);

            adapter.setOverScrollHeight(overScrollHeight);
            layoutParams = (FrameLayout.LayoutParams) mapView.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = overScrollHeight + AndroidUtilities.dp(10);
                if (mapView != null) {
                    mapView.setPadding(0, 0, AndroidUtilities.dp(70), AndroidUtilities.dp(10));
                }
                mapView.setLayoutParams(layoutParams);
            }
            adapter.notifyDataSetChanged();

            if (resume) {
                layoutManager.scrollToPositionWithOffset(0, -AndroidUtilities.dp(32 + (liveLocationType == 1 || liveLocationType == 2 ? 66 : 0)));
                updateClipView(layoutManager.findFirstVisibleItemPosition());
                listView.post(new Runnable() {
                    @Override
                    public void run() {
                        layoutManager.scrollToPositionWithOffset(0, -AndroidUtilities.dp(32 + (liveLocationType == 1 || liveLocationType == 2 ? 66 : 0)));
                        updateClipView(layoutManager.findFirstVisibleItemPosition());
                    }
                });
            } else {
                updateClipView(layoutManager.findFirstVisibleItemPosition());
            }
        }
    }

    private Location getLastLocation() {
        if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && getParentActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Location l = new Location("network");
            l.setLatitude(20.659322);
            l.setLongitude(-11.406250);
            return l;
        } else {
            LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
            List<String> providers = lm.getProviders(true);
            Location l = null;
            for (int i = providers.size() - 1; i >= 0; i--) {
                l = lm.getLastKnownLocation(providers.get(i));
                if (l != null) {
                    break;
                }
            }
            return l;
        }
    }

    private void positionMarker(Location location) {
        if (location == null) {
            return;
        }
        myLocation = new Location(location);
        LiveLocation liveLocation = markersMap.get(UserConfig.getClientUserId());
        LocationController.SharingLocationInfo myInfo = LocationController.getInstance().getSharingLocationInfo(dialogId);
        if (liveLocation != null && myInfo != null && liveLocation.object.id == myInfo.mid) {
            liveLocation.marker.setPosition(new GeoPoint(location.getLatitude(), location.getLongitude()));
        }
        if (messageObject == null && mapView != null) {
            GeoPoint latLng = new GeoPoint(location.getLatitude(), location.getLongitude());
            if (adapter != null) {
                adapter.setGpsLocation(myLocation);
            }
            if (!userLocationMoved) {
                userLocation = new Location(location);
                if (firstWas) {
                    final IMapController controller = mapView.getController();
                    controller.animateTo(latLng);
                } else {
                    firstWas = true;
                    final IMapController controller = mapView.getController();
                    controller.setZoom(mapView.getMaxZoomLevel() - 1);
                    controller.animateTo(latLng);
                }
            }
        } else {
            adapter.setGpsLocation(myLocation);
        }
    }

    public void setMessageObject(MessageObject message) {
        messageObject = message;
        dialogId = messageObject.getDialogId();
    }

    public void setDialogId(long did) {
        dialogId = did;
    }

    private void fetchRecentLocations(ArrayList<TLRPC.Message> messages) {
        BoundingBox builder = null;
        List<GeoPoint> GeoPoints = new ArrayList<>();
        if (firstFocus) {
            builder = null;
        }
        int date = ConnectionsManager.getInstance().getCurrentTime();
        for (int a = 0; a < messages.size(); a++) {
            TLRPC.Message message = messages.get(a);
            if (message.date + message.media.period > date) {
                if (firstFocus) {
                    GeoPoint latLng = new GeoPoint(message.media.geo.lat, message.media.geo._long);
                    GeoPoints.add(latLng);
                }
                addUserMarker(message);
            }
        }
        if (GeoPoints.size()>0) {
            builder = BoundingBox.fromGeoPoints(GeoPoints);
        }
        if (firstFocus) {
            firstFocus = false;
            adapter.setLiveLocations(markers);
            if (messageObject.isLiveLocation()) {
                try {
                    final BoundingBox bounds = builder;
                    if (messages.size() > 1) {
                        try {
                            final IMapController controller = mapView.getController();
                            controller.setZoom(mapView.getMaxZoomLevel() - 1);
                            controller.animateTo(bounds.getCenter());
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                } catch (Exception ignore) {

                }
            }
        }
    }

    private boolean getRecentLocations() {
        ArrayList<TLRPC.Message> messages = LocationController.getInstance().locationsCache.get(messageObject.getDialogId());
        if (messages != null && messages.isEmpty()) {
            fetchRecentLocations(messages);
        } else {
            messages = null;
        }
        TLRPC.TL_messages_getRecentLocations req = new TLRPC.TL_messages_getRecentLocations();
        final long dialog_id = messageObject.getDialogId();
        req.peer = MessagesController.getInputPeer((int) dialog_id);
        req.limit = 100;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                if (response != null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mapView == null) {
                                return;
                            }
                            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                            for (int a = 0; a < res.messages.size(); a++) {
                                if (!(res.messages.get(a).media instanceof TLRPC.TL_messageMediaGeoLive)) {
                                    res.messages.remove(a);
                                    a--;
                                }
                            }
                            MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                            MessagesController.getInstance().putUsers(res.users, false);
                            MessagesController.getInstance().putChats(res.chats, false);
                            LocationController.getInstance().locationsCache.put(dialog_id, res.messages);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.liveLocationsCacheChanged, dialog_id);
                            fetchRecentLocations(res.messages);
                        }
                    });
                }
            }
        });
        return messages != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        } else if (id == NotificationCenter.locationPermissionGranted) {
            if (mapView != null && mapsInitialized) {
                myLocationOverlay.enableMyLocation();
            }
        } else if (id == NotificationCenter.didReceivedNewMessages) {
            long did = (Long) args[0];
            if (did != dialogId || messageObject == null) {
                return;
            }
            ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
            boolean added = false;
            for (int a = 0; a < arr.size(); a++) {
                MessageObject messageObject = arr.get(a);
                if (messageObject.isLiveLocation()) {
                    addUserMarker(messageObject.messageOwner);
                    added = true;
                }
            }
            if (added && adapter != null) {
                adapter.setLiveLocations(markers);
            }
        } else if (id == NotificationCenter.messagesDeleted) {

        } else if (id == NotificationCenter.replaceMessagesObjects) {
            long did = (long) args[0];
            if (did != dialogId || messageObject == null) {
                return;
            }
            boolean updated = false;
            ArrayList<MessageObject> messageObjects = (ArrayList<MessageObject>) args[1];
            for (int a = 0; a < messageObjects.size(); a++) {
                MessageObject messageObject = messageObjects.get(a);
                if (!messageObject.isLiveLocation()) {
                    continue;
                }
                LiveLocation liveLocation = markersMap.get(getMessageId(messageObject.messageOwner));
                if (liveLocation != null) {
                    LocationController.SharingLocationInfo myInfo = LocationController.getInstance().getSharingLocationInfo(did);
                    if (myInfo == null || myInfo.mid != messageObject.getId()) {
                        liveLocation.marker.setPosition(new GeoPoint(messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long));
                    }
                    updated = true;
                }
            }
            if (updated && adapter != null) {
                adapter.updateLiveLocations();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mapView != null && mapsInitialized) {
            myLocationOverlay.disableMyLocation();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);

        if (mapView != null && mapsInitialized) {
            myLocationOverlay.enableMyLocation();
        }
        fixLayoutInternal(true);
        if (checkPermission && Build.VERSION.SDK_INT >= 23) {
            Activity activity = getParentActivity();
            if (activity != null) {
                checkPermission = false;
                if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                }
            }
        }
    }

    public void setDelegate(LocationActivityDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate сellDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor(int color) {

            }
        };
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(locationButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_profile_actionIcon),
                new ThemeDescription(locationButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_profile_actionBackground),
                new ThemeDescription(locationButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_profile_actionPressedBackground),

                new ThemeDescription(routeButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionIcon),
                new ThemeDescription(routeButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionBackground),
                new ThemeDescription(routeButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionPressedBackground),

                new ThemeDescription(markerXImageView, 0, null, null, null, null, Theme.key_location_markerX),

                new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection),

                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.avatar_photoDrawable, Theme.avatar_broadcastDrawable, Theme.avatar_savedDrawable}, сellDelegate, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(null, 0, null, null, null, null, Theme.key_location_liveLocationProgress),
                new ThemeDescription(null, 0, null, null, null, null, Theme.key_location_placeLocationBackground),
                new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialog_liveLocationProgress),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLocationIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLocationBackground),
                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLiveLocationBackground),
                new ThemeDescription(listView, 0, new Class[]{SendLocationCell.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText7),
                new ThemeDescription(listView, 0, new Class[]{SendLocationCell.class}, new String[]{"accurateTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{LocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(listView, 0, new Class[]{LocationCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{LocationCell.class}, new String[]{"addressTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),

                new ThemeDescription(listView, 0, new Class[]{LocationLoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle),
                new ThemeDescription(listView, 0, new Class[]{LocationLoadingCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),

                new ThemeDescription(listView, 0, new Class[]{LocationPoweredCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(listView, 0, new Class[]{LocationPoweredCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(listView, 0, new Class[]{LocationPoweredCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
        };
    }
}