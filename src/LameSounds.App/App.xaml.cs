using System;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace LameSounds.App;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        AppDomain.CurrentDomain.UnhandledException += (s, args) =>
        {
            LogCrash("AppDomain", args.ExceptionObject as Exception);
        };

        DispatcherUnhandledException += (s, args) =>
        {
            LogCrash("Dispatcher", args.Exception);
        };
    }

    private static void LogCrash(string source, Exception? ex)
    {
        try
        {
            var logPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "LameSounds", "crash.log");
            File.AppendAllText(logPath, $"[{DateTime.Now}] [{source}] {ex}\n\n");
        }
        catch { }
    }
}

