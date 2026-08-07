import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Idea2048Game extends JFrame {
    private static final int SIZE = 4;
    private static final int TILE_SIZE = 100;
    private static final int GAP = 10;
    private static final int PADDING = 15;
    private static final int BOARD_SIZE = PADDING * 2 + SIZE * TILE_SIZE + (SIZE - 1) * GAP;

    private final int[][] board = new int[SIZE][SIZE];
    private final Random random = new Random();
    private int score = 0;
    private boolean gameOver = false;

    public Idea2048Game() {
        setTitle("2048 Swing Demo");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setSize(BOARD_SIZE + 16, BOARD_SIZE + 80);
        setLocationRelativeTo(null);

        add(new GamePanel());
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (gameOver) {
                    if (e.getKeyCode() == KeyEvent.VK_R) {
                        restart();
                    }
                    return;
                }

                boolean moved = switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT -> moveLeft();
                    case KeyEvent.VK_RIGHT -> moveRight();
                    case KeyEvent.VK_UP -> moveUp();
                    case KeyEvent.VK_DOWN -> moveDown();
                    case KeyEvent.VK_R -> {
                        restart();
                        yield false;
                    }
                    default -> false;
                };

                if (moved) {
                    addRandomTile();
                    if (isGameOver()) {
                        gameOver = true;
                    }
                    repaint();
                }
            }
        });

        restart();
    }

    private void restart() {
        score = 0;
        gameOver = false;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                board[i][j] = 0;
            }
        }
        addRandomTile();
        addRandomTile();
        repaint();
        requestFocusInWindow();
    }

    private void addRandomTile() {
        List<Point> empties = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == 0) {
                    empties.add(new Point(i, j));
                }
            }
        }
        if (empties.isEmpty()) return;
        Point p = empties.get(random.nextInt(empties.size()));
        board[p.x][p.y] = random.nextDouble() < 0.9 ? 2 : 4;
    }

    private boolean moveLeft() {
        boolean moved = false;
        for (int i = 0; i < SIZE; i++) {
            int[] line = new int[SIZE];
            int index = 0;
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] != 0) line[index++] = board[i][j];
            }
            int[] merged = mergeLine(line);
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] != merged[j]) moved = true;
                board[i][j] = merged[j];
            }
        }
        return moved;
    }

    private boolean moveRight() {
        reverseRows();
        boolean moved = moveLeft();
        reverseRows();
        return moved;
    }

    private boolean moveUp() {
        transpose();
        boolean moved = moveLeft();
        transpose();
        return moved;
    }

    private boolean moveDown() {
        transpose();
        boolean moved = moveRight();
        transpose();
        return moved;
    }

    private int[] mergeLine(int[] line) {
        List<Integer> compact = new ArrayList<>();
        for (int value : line) {
            if (value != 0) compact.add(value);
        }
        List<Integer> result = new ArrayList<>();
        int i = 0;
        while (i < compact.size()) {
            int current = compact.get(i);
            if (i + 1 < compact.size() && compact.get(i + 1) == current) {
                current *= 2;
                score += current;
                i += 2;
            } else {
                i++;
            }
            result.add(current);
        }
        while (result.size() < SIZE) result.add(0);
        int[] arr = new int[SIZE];
        for (int j = 0; j < SIZE; j++) arr[j] = result.get(j);
        return arr;
    }

    private void reverseRows() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE / 2; j++) {
                int t = board[i][j];
                board[i][j] = board[i][SIZE - 1 - j];
                board[i][SIZE - 1 - j] = t;
            }
        }
    }

    private void transpose() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = i + 1; j < SIZE; j++) {
                int t = board[i][j];
                board[i][j] = board[j][i];
                board[j][i] = t;
            }
        }
    }

    private boolean isGameOver() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == 0) return false;
                if (i + 1 < SIZE && board[i][j] == board[i + 1][j]) return false;
                if (j + 1 < SIZE && board[i][j] == board[i][j + 1]) return false;
            }
        }
        return true;
    }

    private class GamePanel extends JPanel {
        GamePanel() {
            setPreferredSize(new Dimension(BOARD_SIZE, BOARD_SIZE + 60));
            setBackground(new Color(0xFAF8EF));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(0x776E65));
            g2.setFont(new Font("SansSerif", Font.BOLD, 28));
            g2.drawString("2048", PADDING, 30);
            g2.setFont(new Font("SansSerif", Font.BOLD, 18));
            g2.drawString("Score: " + score, PADDING + 120, 30);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g2.drawString("Use arrow keys to move. Press R to restart.", PADDING, 52);

            int startY = 70;
            g2.setColor(new Color(0xBBADA0));
            g2.fillRoundRect(PADDING, startY, SIZE * TILE_SIZE + (SIZE - 1) * GAP, SIZE * TILE_SIZE + (SIZE - 1) * GAP, 16, 16);

            for (int i = 0; i < SIZE; i++) {
                for (int j = 0; j < SIZE; j++) {
                    int x = PADDING + j * (TILE_SIZE + GAP);
                    int y = startY + i * (TILE_SIZE + GAP);
                    drawTile(g2, board[i][j], x, y);
                }
            }

            if (gameOver) {
                g2.setColor(new Color(0, 0, 0, 120));
                g2.fillRoundRect(PADDING, startY, SIZE * TILE_SIZE + (SIZE - 1) * GAP, SIZE * TILE_SIZE + (SIZE - 1) * GAP, 16, 16);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 34));
                g2.drawString("Game Over", PADDING + 55, startY + 180);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
                g2.drawString("Press R to restart", PADDING + 70, startY + 215);
            }

            g2.dispose();
        }

        private void drawTile(Graphics2D g2, int value, int x, int y) {
            Color bg = tileColor(value);
            g2.setColor(bg);
            g2.fillRoundRect(x, y, TILE_SIZE, TILE_SIZE, 14, 14);
            if (value != 0) {
                g2.setColor(textColor(value));
                Font font = new Font("SansSerif", Font.BOLD, value < 100 ? 36 : value < 1000 ? 30 : 24);
                g2.setFont(font);
                String text = String.valueOf(value);
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (TILE_SIZE - fm.stringWidth(text)) / 2;
                int ty = y + (TILE_SIZE + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(text, tx, ty);
            }
        }

        private Color tileColor(int value) {
            return switch (value) {
                case 2 -> new Color(0xEEE4DA);
                case 4 -> new Color(0xEDE0C8);
                case 8 -> new Color(0xF2B179);
                case 16 -> new Color(0xF59563);
                case 32 -> new Color(0xF67C5F);
                case 64 -> new Color(0xF65E3B);
                case 128 -> new Color(0xEDCF72);
                case 256 -> new Color(0xEDCC61);
                case 512 -> new Color(0xEDC850);
                case 1024 -> new Color(0xEDC53F);
                case 2048 -> new Color(0xEDC22E);
                default -> new Color(0xCDC1B4);
            };
        }

        private Color textColor(int value) {
            return value <= 4 ? new Color(0x776E65) : Color.WHITE;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Idea2048Game().setVisible(true));
    }
}
