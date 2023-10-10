package com.flower.spirit.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandUtil {

	static Pattern pattern = Pattern.compile("\"(.*?)\"");
	
	public static void command(String command) {
		try {
            ProcessBuilder processBuilder;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
            }

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Read command output
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            System.out.println("Command executed with exit code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
	}
	
	public static String steamcmd(String account,String password,String wallpaper) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("steamcmd", "+login "+account+" "+password+"", "+workshop_download_item 431960 "+wallpaper+"", "+quit");
            Process process = processBuilder.start();
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            String path ="";
            while ((line = reader.readLine()) != null) {
            	 Matcher matcher = pattern.matcher(line);
                 while (matcher.find()) {
                     path = matcher.group(1);
                 }
            }
            int exitCode = process.waitFor();
            System.out.println("SteamCMD执行完毕，退出码：" + exitCode);
            return path;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
		return wallpaper;
	}
	
	public static void main(String[] args) {
		CommandUtil.command("ffmpeg -version");
	}
	
}
