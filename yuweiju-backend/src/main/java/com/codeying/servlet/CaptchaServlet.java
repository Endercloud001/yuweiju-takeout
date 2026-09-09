package com.codeying.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Random;

/**
 * 验证码生成 Servlet。
 *
 * @author Endercloud
 */
@Component
public class CaptchaServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    public static final int WIDTH = 120;
    public static final int HEIGHT = 38;
    public static final int WORDS_NUMBER = 4;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String createTypeFlag = req.getParameter("createTypeFlag");

        BufferedImage bi = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
        Graphics g = bi.getGraphics();
        setBackGround(g);
        setBorder(g);
        drawRandomLine(g);
        String randomString = drawRandomNum((Graphics2D) g, createTypeFlag);

        req.getSession().setAttribute("captcha", randomString);

        resp.setContentType("image/jpeg");
        resp.setDateHeader("expries", -1);
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Pragma", "no-cache");
        ImageIO.write(bi, "jpg", resp.getOutputStream());
    }

    private void setBackGround(Graphics g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private void setBorder(Graphics g) {
        g.setColor(Color.WHITE);
        g.drawRect(1, 1, WIDTH - 2, HEIGHT - 2);
    }

    private void drawRandomLine(Graphics g) {
        g.setColor(Color.GREEN);
        for (int i = 0; i < 3; i++) {
            int x1 = new Random().nextInt(WIDTH);
            int y1 = new Random().nextInt(HEIGHT);
            int x2 = new Random().nextInt(WIDTH);
            int y2 = new Random().nextInt(HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }
    }

    private String drawRandomNum(Graphics g, String createTypeFlag) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("宋体", Font.BOLD, 20));

        String baseNumLetter = "123456789ABCDEFGHJKMNPQRSTUVWXYZ";
        String baseNum = "123456789";
        String baseLetter = "ABCDEFGHJKMNPQRSTUVWXYZ";
        if (createTypeFlag != null && !createTypeFlag.isEmpty()) {
            if ("nl".equals(createTypeFlag)) {
                return createRandomChar((Graphics2D) g, baseNumLetter);
            } else if ("n".equals(createTypeFlag)) {
                return createRandomChar((Graphics2D) g, baseNum);
            } else if ("l".equals(createTypeFlag)) {
                return createRandomChar((Graphics2D) g, baseLetter);
            }
        }
        return createRandomChar((Graphics2D) g, baseNumLetter);
    }

    private String createRandomChar(Graphics2D g, String baseChar) {
        StringBuilder sb = new StringBuilder();
        int x = 5;
        for (int i = 0; i < WORDS_NUMBER; i++) {
            int degree = new Random().nextInt() % 30;
            String ch = baseChar.charAt(new Random().nextInt(baseChar.length())) + "";
            sb.append(ch);

            g.rotate(degree * Math.PI / 180, x, 20);
            g.drawString(ch, x, 20);
            g.rotate(-degree * Math.PI / 180, x, 20);
            x += 30;
        }
        return sb.toString();
    }
}
