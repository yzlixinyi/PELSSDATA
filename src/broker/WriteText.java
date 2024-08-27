package broker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.List;

/**
 * 文本输出工具
 */
public class WriteText {
    WriteText() {
    }

    private static String fileName;

    private static final Logger logger = LogManager.getLogger(WriteText.class);

    public static void createTxtFile(String name, String path) throws IOException {
        createFolder(path);
        fileName = path + name + ".txt";
        File filename = new File(fileName);
        if (!filename.exists() && !filename.createNewFile()) {
            logger.error("Can not create new file {}", fileName);
        }
    }

    public static void setFilePathName(String name, String path) {
        File folder = new File(path);
        if (folder.exists() || folder.mkdirs()) {
            fileName = path + name + ".txt";
        }
    }

    public static void writeLine(String line) {
        File file = new File(fileName);
        try {
            if (file.exists() || file.createNewFile()) {
                appendTxtLine(line);
            }
        } catch (IOException e) {
            logger.error(e);
        }
    }

    /**
     * 一直在原文件中书写
     *
     * @param newStr 新内容
     */
    public static void appendTxtLine(String newStr) {
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName, true)))) {
            out.write(newStr + "\r\n");
        } catch (IOException e) {
            logger.error(e);
        }
    }

    /**
     * @param content 书写的行列表
     * @param path    新文件路径
     */
    static void writeDataList(List<String> content, String path) {
        File fileName = new File(path);
        try {
            if (fileName.exists() || fileName.createNewFile()) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
                    for (String s : content) {
                        writer.write(s + '\n');
                    }
                    writer.flush();
                }
            }
        } catch (IOException e) {
            logger.error(e);
        }
    }

    static void createFolder(String folder) {
        File file = new File(folder);
        if (!file.exists() && !file.mkdirs()) {
            logger.error("Can not create folder {}", folder);
        }
    }
}
