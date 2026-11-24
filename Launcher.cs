using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

namespace HeadSet
{
    public static class Program
    {
        private static string RootDir = AppDomain.CurrentDomain.BaseDirectory;

        [STAThread]
        public static void Main(string[] args)
        {
            try
            {
                // Directly launch the compact PC Widget
                string javaBin = FindJava();
                if (string.IsNullOrEmpty(javaBin))
                {
                    MessageBox.Show(
                        "Java 17 or newer is required to run the HeadSet Widget.\nPlease install a JDK to continue.",
                        "HeadSet Widget",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Warning
                    );
                    return;
                }

                // Determine working directory for widget
                string widgetDir = Path.Combine(RootDir, "HeadSet-Widget-App");
                string serverFile = Path.Combine(widgetDir, "MusicServer.java");
                string workDir = widgetDir;

                if (!File.Exists(serverFile))
                {
                    serverFile = Path.Combine(RootDir, "MusicServer.java");
                    workDir = RootDir;
                }

                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = javaBin,
                    Arguments = "MusicServer.java",
                    WorkingDirectory = workDir,
                    CreateNoWindow = true,
                    UseShellExecute = false
                };
                psi.EnvironmentVariables["HEADSET_WIDGET"] = "1";

                Process.Start(psi);
            }
            catch (Exception ex)
            {
                MessageBox.Show("Could not launch HeadSet Widget: " + ex.Message, "HeadSet Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private static string FindJava()
        {
            // 1. Check if java is in PATH
            try
            {
                Process p = Process.Start(new ProcessStartInfo("where", "java")
                {
                    CreateNoWindow = true,
                    UseShellExecute = false,
                    RedirectStandardOutput = true
                });
                string output = p.StandardOutput.ReadToEnd().Trim();
                p.WaitForExit();
                if (p.ExitCode == 0 && !string.IsNullOrEmpty(output))
                {
                    using (StringReader sr = new StringReader(output))
                    {
                        string first = sr.ReadLine();
                        if (!string.IsNullOrEmpty(first) && File.Exists(first)) return first;
                    }
                    return "java";
                }
            }
            catch { }

            // 2. Check user's specific JDK
            string aaronJdk = @"C:\Users\Aaron John\jdk-25.0.2\bin\java.exe";
            if (File.Exists(aaronJdk)) return aaronJdk;

            // 3. Check JAVA_HOME
            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
            {
                string path = Path.Combine(javaHome, "bin", "java.exe");
                if (File.Exists(path)) return path;
            }

            // 4. Check common JDK directories
            string[] searchRoots = new string[] {
                @"C:\Program Files\Java",
                @"C:\Program Files\Eclipse Adoptium",
                @"C:\Java"
            };

            foreach (string root in searchRoots)
            {
                if (Directory.Exists(root))
                {
                    foreach (string dir in Directory.GetDirectories(root, "jdk*"))
                    {
                        string exe = Path.Combine(dir, "bin", "java.exe");
                        if (File.Exists(exe)) return exe;
                    }
                }
            }

            return null;
        }
    }
}
