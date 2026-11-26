package io.github.local.shizukutoggle;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private ShizukuController controller;
    private TextView status, statusDetail, authBadge, authDisclosure;
    private ImageView statusIcon;
    private ProgressBar statusProgress;
    private LinearLayout authDetails;
    private Switch closeUsbSwitch;
    private LinearLayout usbPermissionHelp;
    private boolean updatingUsbSwitch;
    private int ink, muted, accent, surface, soft, outline, onAccent;
    private final Runnable observer = this::render;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        controller = ShizukuController.get(this);
        ink = getColor(R.color.app_ink);
        muted = getColor(R.color.app_muted);
        accent = getColor(R.color.app_accent);
        surface = getColor(R.color.app_surface);
        soft = getColor(R.color.app_accent_soft);
        outline = getColor(R.color.app_outline);
        onAccent = getColor(R.color.app_on_accent);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(getColor(R.color.app_background));
        LinearLayout content = column();
        content.setPadding(dp(24), dp(28), dp(24), dp(24));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(scroll);
        scroll.requestApplyInsets();

        LinearLayout header = row();
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        header.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout heading = column();
        TextView title = text("Shizuku", 29, ink);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title);
        heading.addView(text("一个磁贴，随手启停", 13, muted));
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1);
        headingParams.leftMargin = dp(14);
        header.addView(heading, headingParams);
        content.addView(header);

        LinearLayout hero = card();
        hero.addView(text("服务状态", 13, muted));
        LinearLayout stateRow = row();
        FrameLayout indicator = new FrameLayout(this);
        indicator.setBackground(shape(soft, 18, false));
        statusIcon = new ImageView(this);
        statusIcon.setImageResource(R.drawable.ic_power);
        statusIcon.setImageTintList(ColorStateList.valueOf(accent));
        indicator.addView(statusIcon, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        statusProgress = new ProgressBar(this);
        statusProgress.setIndeterminateTintList(ColorStateList.valueOf(accent));
        indicator.addView(statusProgress, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        stateRow.addView(indicator, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout statusCopy = column();
        status = text("正在检测", 25, ink);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setId(R.id.status_text);
        statusCopy.addView(status);
        statusDetail = text("正在确认服务状态", 13, muted);
        add(statusCopy, statusDetail, 5);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.leftMargin = dp(16);
        stateRow.addView(statusCopy, copyParams);
        add(hero, stateRow, 20);
        Button addTile = button("添加到快捷设置", true);
        addTile.setId(R.id.add_tile);
        addTile.setOnClickListener(view -> addTile());
        add(hero, addTile, 24);
        TextView manual = text("未显示磁贴？下拉快捷设置，点击编辑并拖入。", 12, muted);
        manual.setGravity(Gravity.CENTER);
        add(hero, manual, 12);
        add(content, hero, 28);

        LinearLayout guide = card();
        TextView guideTitle = text("日常使用，无需打开 App", 15, ink);
        guideTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        guide.addView(guideTitle);
        add(guide, guideRow("1", "点按磁贴", "自动切换启动与停止"), 18);
        add(guide, guideRow("2", "圆环旋转时稍等", "正在执行，完成后自动恢复"), 16);
        add(content, guide, 16);

        LinearLayout stopSettings = card();
        TextView stopTitle = text("停止行为", 15, ink);
        stopTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        stopSettings.addView(stopTitle);
        LinearLayout usbRow = row();
        LinearLayout usbLabels = column();
        usbLabels.addView(text("同时关闭 USB 调试", 15, ink));
        add(usbLabels, text("确认停止后关闭，USB ADB 将断开", 12, muted), 6);
        usbRow.addView(usbLabels, new LinearLayout.LayoutParams(0, -2, 1));
        closeUsbSwitch = new Switch(this);
        closeUsbSwitch.setId(R.id.close_usb_on_stop);
        closeUsbSwitch.setContentDescription("停止 Shizuku 时同时关闭 USB 调试");
        closeUsbSwitch.setMinimumHeight(dp(48));
        closeUsbSwitch.setShowText(false);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(-2, -2);
        switchParams.leftMargin = dp(12);
        usbRow.addView(closeUsbSwitch, switchParams);
        add(stopSettings, usbRow, 14);
        closeUsbSwitch.setChecked(controller.closeUsbOnStop());
        closeUsbSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingUsbSwitch) return;
            if (!controller.setCloseUsbOnStop(checked)) {
                updatingUsbSwitch = true;
                closeUsbSwitch.setChecked(controller.closeUsbOnStop());
                updatingUsbSwitch = false;
                Toast.makeText(this, "请先通过 ADB 完成 USB 调试控制授权", Toast.LENGTH_LONG).show();
            }
        });
        usbPermissionHelp = column();
        usbPermissionHelp.setId(R.id.usb_permission_help);
        add(usbPermissionHelp, text("此选项需要一次 ADB 授权。连接电脑后执行：", 12, muted), 16);
        TextView grantCommand = text(UsbDebugging.GRANT_COMMAND, 12, muted);
        grantCommand.setTypeface(Typeface.MONOSPACE);
        grantCommand.setTextIsSelectable(true);
        add(usbPermissionHelp, grantCommand, 8);
        Button copyGrant = button("复制授权命令", false);
        copyGrant.setId(R.id.copy_usb_grant);
        copyGrant.setOnClickListener(view -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("ADB 授权命令", UsbDebugging.GRANT_COMMAND));
                Toast.makeText(this, "授权命令已复制", Toast.LENGTH_SHORT).show();
            }
        });
        add(usbPermissionHelp, copyGrant, 12);
        stopSettings.addView(usbPermissionHelp);
        usbPermissionHelp.setVisibility(controller.canControlUsb() ? View.GONE : View.VISIBLE);
        add(content, stopSettings, 16);

        LinearLayout authCard = card();
        LinearLayout authHeader = row();
        authHeader.setId(R.id.auth_header);
        authHeader.setMinimumHeight(dp(48));
        authHeader.setClickable(true);
        authHeader.setFocusable(true);
        LinearLayout authLabels = column();
        TextView authTitle = text("授权设置", 16, ink);
        authTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        authLabels.addView(authTitle);
        add(authLabels, text("仅在授权码变更时修改", 12, muted), 4);
        authHeader.addView(authLabels, new LinearLayout.LayoutParams(0, -2, 1));
        authBadge = text("已配置", 12, accent);
        authBadge.setPadding(dp(10), dp(6), dp(10), dp(6));
        authBadge.setBackground(shape(soft, 9, false));
        authHeader.addView(authBadge);
        authDisclosure = text("展开", 12, muted);
        LinearLayout.LayoutParams disclosureParams = new LinearLayout.LayoutParams(-2, -2);
        disclosureParams.leftMargin = dp(12);
        authHeader.addView(authDisclosure, disclosureParams);
        authCard.addView(authHeader);

        authDetails = column();
        authDetails.setId(R.id.auth_details);
        add(authDetails, text("授权码（auth）", 13, muted), 20);
        EditText auth = new EditText(this);
        auth.setId(R.id.auth_input);
        auth.setSingleLine(true);
        auth.setTextSize(15);
        auth.setTextColor(ink);
        auth.setHintTextColor(muted);
        auth.setPadding(dp(14), dp(14), dp(14), dp(14));
        auth.setBackground(shape(getColor(R.color.app_background), 12, true));
        auth.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        auth.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        auth.setText(controller.token());
        auth.setHint("填写 Shizuku 中的 auth");
        add(authDetails, auth, 10);
        Button save = button("保存授权码", false);
        save.setId(R.id.save_auth);
        save.setOnClickListener(view -> {
            if (controller.saveToken(auth.getText().toString())) {
                auth.setText(controller.token());
                auth.clearFocus();
                Toast.makeText(this, "授权码已保存", Toast.LENGTH_SHORT).show();
            } else auth.setError("授权码不能为空");
        });
        add(authDetails, save, 12);
        authCard.addView(authDetails);
        boolean expanded = savedInstanceState != null ? savedInstanceState.getBoolean("auth_expanded")
                : controller.token().isEmpty();
        setAuthExpanded(expanded);
        authHeader.setOnClickListener(view -> setAuthExpanded(authDetails.getVisibility() != View.VISIBLE));
        add(content, authCard, 16);

        Button open = button("打开 Shizuku  ↗", false);
        open.setOnClickListener(view -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(BroadcastProtocol.PACKAGE);
            if (launch == null) Toast.makeText(this, "未找到 Shizuku", Toast.LENGTH_SHORT).show();
            else startActivity(launch);
        });
        add(content, open, 20);
        TextView footer = text("无需常驻  ·  点击即用", 12, muted);
        footer.setGravity(Gravity.CENTER);
        add(content, footer, 18);
    }

    private void setAuthExpanded(boolean expanded) {
        authDetails.setVisibility(expanded ? View.VISIBLE : View.GONE);
        authDisclosure.setText(expanded ? "收起" : "展开");
        View header = findViewById(R.id.auth_header);
        if (header != null) header.setContentDescription("授权设置，点击" + (expanded ? "收起" : "展开"));
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("auth_expanded", authDetails.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(out);
    }
    @Override public void onStart() {
        super.onStart();
        controller.addObserver(observer);
    }
    @Override public void onStop() {
        controller.removeObserver(observer);
        super.onStop();
    }

    private void render() {
        ShizukuController.ViewState view = controller.view();
        boolean busy = view.pending != null;
        boolean detecting = view.actual == ToggleState.Actual.UNKNOWN && view.message.equals("正在检测…");
        statusProgress.setVisibility(busy || detecting ? View.VISIBLE : View.GONE);
        statusIcon.setVisibility(busy || detecting ? View.INVISIBLE : View.VISIBLE);
        if (busy) {
            status.setText(view.pending == ToggleState.Command.START ? "正在启动" : "正在停止");
        } else if (view.actual == ToggleState.Actual.RUNNING) {
            status.setText("正在运行");
        } else if (view.actual == ToggleState.Actual.STOPPED) {
            status.setText("已停止");
        } else {
            status.setText(detecting ? "正在检测" : "暂不可用");
        }
        status.setTextColor(view.actual == ToggleState.Actual.RUNNING && !busy ? accent : ink);
        statusDetail.setText(detailText(view));
        statusDetail.setTextColor(view.needsAttention && !busy ? getColor(R.color.app_error) : muted);
        authBadge.setText(view.configured ? "已配置" : "待配置");
        updatingUsbSwitch = true;
        closeUsbSwitch.setChecked(controller.closeUsbOnStop());
        updatingUsbSwitch = false;
        usbPermissionHelp.setVisibility(controller.canControlUsb() ? View.GONE : View.VISIBLE);
    }

    static String detailText(ShizukuController.ViewState view) {
        if (view.pending != null) return "请稍候，正在等待服务响应";
        if (view.needsAttention) return view.message;
        if (view.actual == ToggleState.Actual.RUNNING)
            return "点击磁贴即可停止服务";
        if (view.actual == ToggleState.Actual.STOPPED)
            return "点击磁贴即可启动服务";
        return view.message;
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager manager = getSystemService(StatusBarManager.class);
            if (manager != null) {
                manager.requestAddTileService(new ComponentName(this, ShizukuTileService.class),
                        "Shizuku", Icon.createWithResource(this, R.drawable.ic_tile), getMainExecutor(), result -> {
                            String message = switch (result) {
                                case StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "磁贴已添加";
                                case StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "磁贴已存在，可下拉快捷设置查看";
                                default -> "可下拉快捷设置，点击编辑后手动添加 Shizuku";
                            };
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        });
                return;
            }
        }
        Toast.makeText(this, "请下拉快捷设置，点击编辑，将 Shizuku 磁贴拖入", Toast.LENGTH_LONG).show();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }
    private LinearLayout card() {
        LinearLayout layout = column();
        layout.setPadding(dp(22), dp(22), dp(22), dp(22));
        layout.setBackground(shape(surface, 24, false));
        return layout;
    }
    private View guideRow(String number, String title, String description) {
        LinearLayout row = row();
        TextView marker = text(number, 13, accent);
        marker.setGravity(Gravity.CENTER);
        marker.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        marker.setBackground(shape(soft, 11, false));
        row.addView(marker, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout labels = column();
        labels.addView(text(title, 14, ink));
        add(labels, text(description, 12, muted), 3);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = dp(12);
        row.addView(labels, params);
        return row;
    }
    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setLineSpacing(dp(2), 1f);
        text.setIncludeFontPadding(false);
        return text;
    }
    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? onAccent : accent);
        button.setMinHeight(dp(54));
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24808080),
                shape(primary ? accent : surface, 15, !primary), null));
        return button;
    }
    private GradientDrawable shape(int color, int radius, boolean border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (border) drawable.setStroke(dp(1), outline);
        return drawable;
    }
    private void add(LinearLayout parent, View view, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topMargin);
        parent.addView(view, params);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
