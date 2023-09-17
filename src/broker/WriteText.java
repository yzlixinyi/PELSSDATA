package broker;


import java.io.*;
import java.util.List;

import static java.lang.System.Logger.Level.ERROR;

/**
 * 文本输出工具
 */
public class WriteText {
    WriteText() {
    }

    private static String fileName;

    private static final System.Logger logger = System.getLogger(WriteText.class.getSimpleName());

    public static void createTxtFile(String name, String path) throws IOException {
        createFolder(path);
        fileName = path + name + ".txt";
        File filename = new File(fileName);
        if (!filename.exists()) {
            boolean newFile = filename.createNewFile();
            if (!newFile) {
                logger.log(ERROR, "Can not create new file.");
            }
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
            logger.log(ERROR, e);
        }
    }

    /**
     * @param content 书写的行列表
     * @param path    新文件路径
     */
    static void writeDataList(List<String> content, String path) throws IOException {
        File fileName = new File(path);
        if (!fileName.exists()) {
            boolean newFile = fileName.createNewFile();
            if (!newFile) {
                logger.log(ERROR, "Can not create new file.");
            }
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (String s : content) {
                writer.write(s + '\n');
            }
            writer.flush();
        }
    }

    static void createFolder(String folder) {
        try {
            File file = new File(folder);
            if (!file.exists()) {
                boolean mks = file.mkdirs();
                if (!mks) logger.log(ERROR, "False file folder.");
            }
        } catch (Exception e) {
            logger.log(ERROR, e);
        }
    }
}
