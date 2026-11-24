using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Windows.Forms;

namespace HeadSet
{
    public class LauncherForm : Form
    {
        private static string RootDir = AppDomain.CurrentDomain.BaseDirectory;
        private Timer autoStartTimer;
        private int countdown = 4;
        private Label lblCountdown;
        private Button btnDesktop;
        private Button btnWidget;
        private Button btnBuild;
        private CheckBox chkRemember;
        private string configFile = Path.Combine(RootDir, ".headset_launcher.cfg");

        public LauncherForm()
        {
            this.Text = "HeadSet — Music Player & Widget";
            this.Size = new Size(460, 360);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.FormBorderStyle = FormBorderStyle.FixedSingle;
            this.MaximizeBox = false;
            this.BackColor = Color.FromArgb(15, 13, 26);
            this.ForeColor = Color.FromArgb(240, 238, 255);
            this.Font = new Font("Segoe UI", 9.5f, FontStyle.Regular);

            // App Icon
            try
            {
                this.Icon = GenerateIcon();
            }
            catch { }

            // Header Icon + Brand
            PictureBox pbLogo = new PictureBox();
            pbLogo.Size = new Size(44, 44);
            pbLogo.Location = new Point(28, 24);
            pbLogo.Paint += (s, e) =>
            {
                e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
                using (SolidBrush b = new SolidBrush(Color.FromArgb(142, 117, 255)))
                {
                    e.Graphics.FillEllipse(b, 2, 2, 40, 40);
                }
                using (Pen p = new Pen(Color.White, 3))
                {
                    p.StartCap = LineCap.Round;
                    p.EndCap = LineCap.Round;
                    e.Graphics.DrawArc(p, 10, 8, 24, 18, 180, 180);
                }
                using (SolidBrush eb = new SolidBrush(Color.White))
                {
                    e.Graphics.FillRectangle(eb, 7, 20, 6, 10);
                    e.Graphics.FillRectangle(eb, 31, 20, 6, 10);
                }
            };
            this.Controls.Add(pbLogo);

            Label lblTitle = new Label();
            lblTitle.Text = "HeadSet";
            lblTitle.Font = new Font("Segoe UI", 16f, FontStyle.Bold);
            lblTitle.ForeColor = Color.FromArgb(240, 238, 255);
            lblTitle.Location = new Point(82, 22);
            lblTitle.AutoSize = true;
            this.Controls.Add(lblTitle);

            Label lblSub = new Label();
            lblSub.Text = "Your personal sound • Desktop Player & Widget";
            lblSub.Font = new Font("Segoe UI", 9.5f, FontStyle.Regular);
            lblSub.ForeColor = Color.FromArgb(138, 133, 168);
            lblSub.Location = new Point(84, 52);
            lblSub.AutoSize = true;
            this.Controls.Add(lblSub);

            // Divider
            Panel div = new Panel();
            div.Size = new Size(390, 1);
            div.Location = new Point(28, 86);
            div.BackColor = Color.FromArgb(38, 32, 64);
            this.Controls.Add(div);

            // Option 1: Desktop Player
            btnDesktop = CreateActionButton(
                "🎵  Launch Desktop Player (Full App)",
                "Native window with hero visualizer, 10-band EQ & library",
                28, 104,
                (s, e) => LaunchDesktop()
            );
            this.Controls.Add(btnDesktop);

            // Option 2: PC Widget
            btnWidget = CreateActionButton(
                "🎛  Launch Compact PC Widget",
                "Compact borderless widget for multitasking (Edge/Chrome app mode)",
                28, 172,
                (s, e) => LaunchWidget()
            );
            this.Controls.Add(btnWidget);

            // Option 3: Build & Package
            btnBuild = new Button();
            btnBuild.Text = "⚙  Rebuild / Package Standalone EXE";
            btnBuild.Font = new Font("Segoe UI", 8.5f, FontStyle.Regular);
            btnBuild.ForeColor = Color.FromArgb(138, 133, 168);
            btnBuild.BackColor = Color.FromArgb(22, 19, 39);
            btnBuild.FlatStyle = FlatStyle.Flat;
            btnBuild.FlatAppearance.BorderColor = Color.FromArgb(38, 32, 64);
            btnBuild.Size = new Size(390, 32);
            btnBuild.Location = new Point(28, 240);
            btnBuild.Cursor = Cursors.Hand;
            btnBuild.Click += (s, e) => RunBuild();
            this.Controls.Add(btnBuild);

            // Checkbox: Remember Choice
            chkRemember = new CheckBox();
            chkRemember.Text = "Remember my choice and skip this launcher";
            chkRemember.ForeColor = Color.FromArgb(138, 133, 168);
            chkRemember.Font = new Font("Segoe UI", 8.5f);
            chkRemember.Location = new Point(28, 280);
            chkRemember.AutoSize = true;
            this.Controls.Add(chkRemember);

            // Auto-start Countdown Label
            lblCountdown = new Label();
            lblCountdown.Text = "Auto-starting Desktop in 4s... (click any option to choose)";
            lblCountdown.Font = new Font("Segoe UI", 8f, FontStyle.Italic);
            lblCountdown.ForeColor = Color.FromArgb(181, 163, 255);
            lblCountdown.Location = new Point(28, 305);
            lblCountdown.AutoSize = true;
            this.Controls.Add(lblCountdown);

            // Stop timer on any mouse hover or interaction
            this.MouseMove += (s, e) => StopCountdown();
            btnDesktop.MouseEnter += (s, e) => StopCountdown();
            btnWidget.MouseEnter += (s, e) => StopCountdown();
            btnBuild.MouseEnter += (s, e) => StopCountdown();
            chkRemember.CheckedChanged += (s, e) => StopCountdown();

            // Auto-start Timer
            autoStartTimer = new Timer();
            autoStartTimer.Interval = 1000;
            autoStartTimer.Tick += (s, e) =>
            {
                countdown--;
                if (countdown > 0)
                {
                    lblCountdown.Text = "Auto-starting Desktop in " + countdown + "s... (click any option to choose)";
                }
                else
                {
                    autoStartTimer.Stop();
                    LaunchDesktop();
                }
            };
            autoStartTimer.Start();
        }

        private void StopCountdown()
        {
            if (autoStartTimer != null && autoStartTimer.Enabled)
            {
                autoStartTimer.Stop();
                lblCountdown.Text = "Select an option above to launch:";
                lblCountdown.ForeColor = Color.FromArgb(138, 133, 168);
            }
        }

        private Button CreateActionButton(string title, string subtitle, int x, int y, EventHandler onClick)
        {
            Button btn = new Button();
            btn.Text = title + "\n  " + subtitle;
            btn.TextAlign = ContentAlignment.MiddleLeft;
            btn.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            btn.ForeColor = Color.FromArgb(240, 238, 255);
            btn.BackColor = Color.FromArgb(22, 19, 39);
            btn.FlatStyle = FlatStyle.Flat;
            btn.FlatAppearance.BorderColor = Color.FromArgb(43, 35, 77);
            btn.FlatAppearance.BorderSize = 1;
            btn.Size = new Size(390, 56);
            btn.Location = new Point(x, y);
            btn.Cursor = Cursors.Hand;
            btn.Padding = new Padding(12, 4, 12, 4);

            btn.MouseEnter += (s, e) =>
            {
                btn.BackColor = Color.FromArgb(35, 28, 65);
                btn.FlatAppearance.BorderColor = Color.FromArgb(142, 117, 255);
            };
            btn.MouseLeave += (s, e) =>
            {
                btn.BackColor = Color.FromArgb(22, 19, 39);
                btn.FlatAppearance.BorderColor = Color.FromArgb(43, 35, 77);
            };
            btn.Click += onClick;
            return btn;
        }

        public void LaunchDesktop()
        {
            SaveConfigIfRequested("desktop");
            this.Hide();

            // Check if native packaged EXE exists
            string exe1 = Path.Combine(RootDir, "dist", "HeadSet", "HeadSet.exe");
            string exe2 = @"C:\HeadSet-Build\output\HeadSet\HeadSet.exe";
            if (File.Exists(exe1))
            {
                Process.Start(new ProcessStartInfo(exe1) { WorkingDirectory = Path.GetDirectoryName(exe1) });
                Application.Exit();
                return;
            }
            if (File.Exists(exe2))
            {
                Process.Start(new ProcessStartInfo(exe2) { WorkingDirectory = Path.GetDirectoryName(exe2) });
                Application.Exit();
                return;
            }

            // Fallback: run via run.bat
            string runBat = Path.Combine(RootDir, "run.bat");
            if (File.Exists(runBat))
            {
                ProcessStartInfo psi = new ProcessStartInfo("cmd.exe", "/c \"" + runBat + "\" desktop")
                {
                    WorkingDirectory = RootDir,
                    CreateNoWindow = true,
                    UseShellExecute = false
                };
                Process.Start(psi);
            }
            Application.Exit();
        }

        public void LaunchWidget()
        {
            SaveConfigIfRequested("widget");
            this.Hide();

            string widgetBat1 = Path.Combine(RootDir, "HeadSet-Widget-App", "START_WIDGET_WINDOWS.bat");
            string widgetBat2 = Path.Combine(RootDir, "START_WIDGET_WINDOWS.bat");
            string runBat = Path.Combine(RootDir, "run.bat");

            if (File.Exists(widgetBat1))
            {
                ProcessStartInfo psi = new ProcessStartInfo("cmd.exe", "/c \"" + widgetBat1 + "\"")
                {
                    WorkingDirectory = Path.GetDirectoryName(widgetBat1),
                    CreateNoWindow = true,
                    UseShellExecute = false
                };
                Process.Start(psi);
            }
            else if (File.Exists(widgetBat2))
            {
                ProcessStartInfo psi = new ProcessStartInfo("cmd.exe", "/c \"" + widgetBat2 + "\"")
                {
                    WorkingDirectory = RootDir,
                    CreateNoWindow = true,
                    UseShellExecute = false
                };
                Process.Start(psi);
            }
            else if (File.Exists(runBat))
            {
                ProcessStartInfo psi = new ProcessStartInfo("cmd.exe", "/c \"" + runBat + "\" widget")
                {
                    WorkingDirectory = RootDir,
                    CreateNoWindow = true,
                    UseShellExecute = false
                };
                Process.Start(psi);
            }
            Application.Exit();
        }

        public void RunBuild()
        {
            this.Hide();
            string buildBat = Path.Combine(RootDir, "build.bat");
            if (File.Exists(buildBat))
            {
                ProcessStartInfo psi = new ProcessStartInfo("cmd.exe", "/c \"" + buildBat + "\"")
                {
                    WorkingDirectory = RootDir
                };
                Process.Start(psi);
            }
            Application.Exit();
        }

        private void SaveConfigIfRequested(string mode)
        {
            if (chkRemember.Checked)
            {
                try
                {
                    File.WriteAllText(configFile, mode);
                }
                catch { }
            }
        }

        public static Icon GenerateIcon()
        {
            using (Bitmap b = new Bitmap(64, 64))
            using (Graphics g = Graphics.FromImage(b))
            {
                g.SmoothingMode = SmoothingMode.AntiAlias;
                using (SolidBrush br = new SolidBrush(Color.FromArgb(142, 117, 255)))
                {
                    g.FillEllipse(br, 2, 2, 60, 60);
                }
                using (Pen p = new Pen(Color.White, 4))
                {
                    p.StartCap = LineCap.Round;
                    p.EndCap = LineCap.Round;
                    g.DrawArc(p, 16, 12, 32, 24, 180, 180);
                }
                using (SolidBrush eb = new SolidBrush(Color.White))
                {
                    g.FillRectangle(eb, 12, 28, 8, 14);
                    g.FillRectangle(eb, 44, 28, 8, 14);
                }
                IntPtr hIcon = b.GetHicon();
                return Icon.FromHandle(hIcon);
            }
        }

        [STAThread]
        public static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            string config = Path.Combine(RootDir, ".headset_launcher.cfg");

            // Check command line arguments
            if (args != null && args.Length > 0)
            {
                string arg = args[0].ToLowerInvariant();
                if (arg == "--desktop" || arg == "-d" || arg == "desktop")
                {
                    new LauncherForm().LaunchDesktop();
                    return;
                }
                if (arg == "--widget" || arg == "-w" || arg == "widget")
                {
                    new LauncherForm().LaunchWidget();
                    return;
                }
                if (arg == "--build" || arg == "-b" || arg == "build")
                {
                    new LauncherForm().RunBuild();
                    return;
                }
            }

            // Check if user saved preference (holding Shift bypasses preference)
            if (File.Exists(config) && (Control.ModifierKeys & Keys.Shift) == 0)
            {
                try
                {
                    string saved = File.ReadAllText(config).Trim().ToLowerInvariant();
                    if (saved == "desktop")
                    {
                        new LauncherForm().LaunchDesktop();
                        return;
                    }
                    if (saved == "widget")
                    {
                        new LauncherForm().LaunchWidget();
                        return;
                    }
                }
                catch { }
            }

            Application.Run(new LauncherForm());
        }
    }
}
