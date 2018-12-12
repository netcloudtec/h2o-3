package water;

/**
 * @Author: yangshaojun
 * @Date: 2018/12/10 14:12
 * @Version 1.0
 */
public class Test {
    public static void main(String[] args) {
        String str="/data/admin/aaa/dd/_SUCCESS";
        int lastIndexOf = str.lastIndexOf("/");
        String name = str.substring(0,lastIndexOf);
        String path=str.substring(0,lastIndexOf);
        System.out.println(name);
    }
}
