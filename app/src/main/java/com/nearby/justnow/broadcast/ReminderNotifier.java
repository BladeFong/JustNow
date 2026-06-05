package com.nearby.justnow.broadcast;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.scheduler.ReminderScheduler;
import com.nearby.justnow.util.DateUtils;

/**
 * 构建和发送提醒通知，含动态操作按钮。
 */
public class ReminderNotifier {

    public static final String CHANNEL_ID = "task_reminder";
    static final int NOTIFICATION_ID_BASE = 7000;
    static final String ACTION_POSTPONE = "com.nearby.justnow.ACTION_POSTPONE";
    public static final String ACTION_START = "com.nearby.justnow.ACTION_START";
    public static final String ACTION_IGNORE = "com.nearby.justnow.ACTION_IGNORE";

    /** 创建通知渠道（首次调用时执行）。 */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.s_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(context.getString(R.string.s_notification_channel_desc));
            channel.setShowBadge(false);
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build();
            channel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                attrs);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    /** 发送提醒通知。 */
    public static void send(Context context, TaskScheduleEntity schedule, TaskEntity task,
                            boolean hasRunningTask, boolean isRunningChore,
                            boolean canPostpone15, boolean canPostpone30) {
        String title = task.content;
        String body = context.getString(R.string.s_notification_body,
            DateUtils.formatMinute(schedule.scheduledTime));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH);

        // 点击通知本体 → 打开详情页查看内容
        Intent tapIntent = buildDetailIntent(context, schedule, task);
        builder.setContentIntent(PendingIntent.getActivity(context, requestCode(schedule.id),
            tapIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // "忽略" 按钮（最左）→ 广播通知 AlarmReceiver 执行忽略逻辑
        Intent ignoreIntent = buildIgnoreIntent(context, schedule, task);
        builder.addAction(R.drawable.ic_launcher_foreground,
            context.getString(R.string.s_ignore),
            PendingIntent.getBroadcast(context, requestCode(schedule.id) + 4,
                ignoreIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // "开始" 按钮 → 广播通知 AlarmReceiver 直接 startExecution
        Intent startIntent = buildStartIntent(context, schedule, task);
        builder.addAction(R.drawable.ic_launcher_foreground,
            context.getString(R.string.s_start_now),
            PendingIntent.getBroadcast(context, requestCode(schedule.id) + 1,
                startIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // 延迟按钮（仅非琐碎阻塞时显示）
        if (!isRunningChore) {
            if (canPostpone15) {
                Intent postpone15Intent = buildPostponeIntent(context, schedule, task, 15);
                builder.addAction(0, context.getString(R.string.s_postpone_15),
                    PendingIntent.getBroadcast(context, requestCode(schedule.id) + 2,
                        postpone15Intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
            if (canPostpone30) {
                Intent postpone30Intent = buildPostponeIntent(context, schedule, task, 30);
                builder.addAction(0, context.getString(R.string.s_postpone_30),
                    PendingIntent.getBroadcast(context, requestCode(schedule.id) + 3,
                        postpone30Intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        }

        NotificationManagerCompat.from(context).notify(notificationId(schedule.id), builder.build());
    }

    /** 取消提醒通知。 */
    public static void cancel(Context context, long scheduleId) {
        NotificationManagerCompat.from(context).cancel(notificationId(scheduleId));
    }

    static int notificationId(long scheduleId) {
        return NOTIFICATION_ID_BASE + (int) (scheduleId & 0x7FFFFFFF);
    }

    /** 通知本体点击 → 详情页查看内容（底部无按钮）。 */
    private static Intent buildDetailIntent(Context context, TaskScheduleEntity schedule,
                                             TaskEntity task) {
        Intent intent = new Intent(context,
            com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.class);
        intent.putExtra("task_id", task.id);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    /** "开始" 按钮 → 广播，AlarmReceiver 直接 startExecution。 */
    private static Intent buildStartIntent(Context context, TaskScheduleEntity schedule,
                                            TaskEntity task) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ReminderScheduler.ACTION_START_TASK);
        intent.putExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, schedule.id);
        intent.putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id);
        return intent;
    }

    /** "忽略" 按钮 → 广播，AlarmReceiver 执行忽略逻辑。 */
    private static Intent buildIgnoreIntent(Context context, TaskScheduleEntity schedule,
                                            TaskEntity task) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_IGNORE);
        intent.putExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, schedule.id);
        intent.putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id);
        intent.putExtra(ReminderScheduler.EXTRA_SCHEDULE_TYPE, schedule.scheduleType);
        intent.putExtra(ReminderScheduler.EXTRA_SCHEDULED_TIME, schedule.scheduledTime);
        return intent;
    }

    static Intent buildPostponeIntent(Context context, TaskScheduleEntity schedule,
                                       TaskEntity task, int postponeMinutes) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_POSTPONE);
        intent.putExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, schedule.id);
        intent.putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id);
        intent.putExtra(ReminderScheduler.EXTRA_SCHEDULE_TYPE, schedule.scheduleType);
        intent.putExtra(ReminderScheduler.EXTRA_SCHEDULED_TIME, schedule.scheduledTime);
        intent.putExtra("postpone_minutes", postponeMinutes);
        return intent;
    }

    private static int requestCode(long scheduleId) {
        return (int) ((scheduleId * 31) & 0x7FFFFFFF);
    }
}
