package com.mkac.meikomms.common;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TimeUtils
{

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static long getCurrentUnixTimestamp() {
        // Get the current date and time
        LocalDateTime now = LocalDateTime.now();

        // Convert the date and time to a Unix timestamp
        Instant instant = now.toInstant(ZoneOffset.UTC);
        long unixTimestamp = instant.getEpochSecond();
        return unixTimestamp;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String getCurrentDateString() {
        // Get the current date and time
        LocalDateTime now = LocalDateTime.now();

        // Define a formatter to format the date and time
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Format the date and time
        String formattedDateTime = now.format(formatter);

        // Print the formatted date and time
        // System.out.println("Current date and time: " + formattedDateTime);
        return formattedDateTime;
    }

    public static String getCurrentDateStringByFormat(String format)
    {
        // Get the current date and time
        LocalDateTime now = LocalDateTime.now();

        // Define a formatter to format the date and time
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);

        // Format the date and time
        String formattedDateTime = now.format(formatter);

        // Print the formatted date and time
        // System.out.println("Current date and time: " + formattedDateTime);
        return formattedDateTime;
    }

    public static String getCurrentTimeString() {
        // Get the current date and time
        LocalDateTime now = LocalDateTime.now();

        // Define a formatter to format the date and time
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        // Format the date and time
        String formattedDateTime = now.format(formatter);

        // Print the formatted date and time
        // System.out.println("Current date and time: " + formattedDateTime);
        return formattedDateTime;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static Long dateToLong(String formattedDateTime) {
        // Define the formatter to parse the string
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Parse the string into a LocalDateTime object
        LocalDateTime dateTime = LocalDateTime.parse(formattedDateTime, formatter);

        // Convert the LocalDateTime to an Instant
        Instant instant = dateTime.toInstant(ZoneOffset.UTC);

        // Get the Unix timestamp (number of seconds since the epoch)
        long unixTimestamp = instant.getEpochSecond();

        // Print the Unix timestamp
        // System.out.println("Unix timestamp: " + unixTimestamp);
        return unixTimestamp;
    }

    public static Long localdateToLong(LocalDateTime dateTime)
    {
        String  formattedDateTime = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        // Define the formatter to parse the string
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Convert the LocalDateTime to an Instant
        Instant instant = dateTime.toInstant(ZoneOffset.UTC);

        // Get the Unix timestamp (number of seconds since the epoch)
        long unixTimestamp = instant.getEpochSecond();

        return unixTimestamp;
    }

    public static String getFirstDayOfMonth(String format) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1); // Đặt ngày về ngày đầu tiên của tháng
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
        return sdf.format(calendar.getTime());
    }

    public static String getLastDayOfMonth(String format) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)); // Lấy ngày cuối cùng
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
        return sdf.format(calendar.getTime());
    }

    public static long getUnixTimeMillisFromString(String dateStr)
    {
        // Define the formatter to parse the string
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // Parse the string into a LocalDateTime object
        LocalDate  dateTime = LocalDate .parse(dateStr, formatter);

        // Convert the LocalDateTime to an Instant
        Instant instant = dateTime.atStartOfDay(ZoneOffset.UTC).toInstant();

        // Get the Unix timestamp (number of seconds since the epoch)
        long unixTimestamp = instant.getEpochSecond();

        // Print the Unix timestamp
        // System.out.println("Unix timestamp: " + unixTimestamp);
        return unixTimestamp;
    }

    public static String convertUnixToDateString(long unixTimestampMillis) {
        // Tạo đối tượng Date từ unix timestamp (đơn vị milliseconds)
        Date date = new Date(unixTimestampMillis);

        // Định dạng theo "dd/MM/yyyy"
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        return formatter.format(date);
    }


}

