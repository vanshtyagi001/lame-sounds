using System;
using System.IO;
using System.Text.Json;
using LameSounds.Core.Models;

namespace LameSounds.Core.Services
{
    public class SettingsService
    {
        private readonly string _configFile;
        private AudioConfig _config;

        public AudioConfig Config => _config;

        public event EventHandler<AudioConfig>? ConfigChanged;

        public SettingsService(string? customDir = null)
        {
            var dir = customDir ?? Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "LameSounds"
            );
            Directory.CreateDirectory(dir);
            _configFile = Path.Combine(dir, "config.json");
            _config = LoadConfig();
        }

        public AudioConfig LoadConfig()
        {
            if (File.Exists(_configFile))
            {
                try
                {
                    var json = File.ReadAllText(_configFile);
                    var loaded = JsonSerializer.Deserialize<AudioConfig>(json);
                    if (loaded != null)
                    {
                        _config = loaded;
                        return _config;
                    }
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[SettingsService] Load error: {ex.Message}");
                }
            }

            _config = new AudioConfig();
            SaveConfig();
            return _config;
        }

        public void SaveConfig()
        {
            try
            {
                var json = JsonSerializer.Serialize(_config, new JsonSerializerOptions { WriteIndented = true });
                File.WriteAllText(_configFile, json);
                ConfigChanged?.Invoke(this, _config);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[SettingsService] Save error: {ex.Message}");
            }
        }
    }
}
