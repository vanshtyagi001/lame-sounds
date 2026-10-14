using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using LameSounds.Core.Audio;
using LameSounds.Core.Models;
using NAudio.Wave;

namespace LameSounds.Core.Services
{
    public class SoundLibraryService
    {
        private readonly string _storageDir;
        private readonly string _soundsDir;
        private readonly string _metaFile;
        private readonly List<SoundClip> _sounds = new();
        private readonly AudioEngine _audioEngine;
        private readonly object _lock = new();

        public IReadOnlyList<SoundClip> Sounds
        {
            get
            {
                lock (_lock) return _sounds.ToList();
            }
        }

        public event EventHandler? LibraryChanged;

        public SoundLibraryService(AudioEngine audioEngine, string? customStorageDir = null)
        {
            _audioEngine = audioEngine;
            _storageDir = customStorageDir ?? Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "LameSounds"
            );
            _soundsDir = Path.Combine(_storageDir, "sounds");
            _metaFile = Path.Combine(_storageDir, "sounds.json");

            Directory.CreateDirectory(_storageDir);
            Directory.CreateDirectory(_soundsDir);

            LoadLibrary();
        }

        public void LoadLibrary()
        {
            lock (_lock)
            {
                _sounds.Clear();

                if (File.Exists(_metaFile))
                {
                    try
                    {
                        var json = File.ReadAllText(_metaFile);
                        var loaded = JsonSerializer.Deserialize<List<SoundClip>>(json);
                        if (loaded != null && loaded.Count > 0)
                        {
                            _sounds.AddRange(loaded);
                        }
                    }
                    catch (Exception ex)
                    {
                        System.Diagnostics.Debug.WriteLine($"[SoundLibrary] Load error: {ex.Message}");
                    }
                }

                // Sound library starts clean and empty unless user adds sounds
                // Pre-cache sounds into AudioEngine and ensure FileSizeBytes is populated
                foreach (var sound in _sounds)
                {
                    if (sound.FileSizeBytes <= 0 && !string.IsNullOrEmpty(sound.FilePath) && File.Exists(sound.FilePath))
                    {
                        try { sound.FileSizeBytes = new FileInfo(sound.FilePath).Length; } catch { }
                    }
                    _audioEngine.LoadSound(sound);
                }
            }

            LibraryChanged?.Invoke(this, EventArgs.Empty);
        }

        public void SaveLibrary()
        {
            lock (_lock)
            {
                try
                {
                    var json = JsonSerializer.Serialize(_sounds, new JsonSerializerOptions { WriteIndented = true });
                    File.WriteAllText(_metaFile, json);
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[SoundLibrary] Save error: {ex.Message}");
                }
            }
        }

        public SoundClip AddSound(
            string sourceFilePath,
            string title,
            string? hotkey = null,
            string? colorHex = null,
            string? icon = null,
            string? category = null,
            string? gradientHex = null,
            string? imageFilePath = null)
        {
            var id = Guid.NewGuid().ToString();
            var ext = Path.GetExtension(sourceFilePath);
            var destFileName = $"{id}{ext}";
            var destPath = Path.Combine(_soundsDir, destFileName);

            File.Copy(sourceFilePath, destPath, true);

            string? destImagePath = null;
            if (!string.IsNullOrWhiteSpace(imageFilePath) && File.Exists(imageFilePath))
            {
                try
                {
                    var imgExt = Path.GetExtension(imageFilePath);
                    var destImgName = $"{id}_img{imgExt}";
                    destImagePath = Path.Combine(_soundsDir, destImgName);
                    File.Copy(imageFilePath, destImagePath, true);
                }
                catch { }
            }

            double duration = 0;
            try
            {
                using var reader = new AudioFileReader(destPath);
                duration = reader.TotalTime.TotalSeconds;
            }
            catch { }

            long fileSize = 0;
            try { fileSize = new FileInfo(destPath).Length; } catch { }

            var clip = new SoundClip
            {
                Id = id,
                Title = string.IsNullOrWhiteSpace(title) ? Path.GetFileNameWithoutExtension(sourceFilePath) : title,
                FileName = destFileName,
                FilePath = destPath,
                Hotkey = hotkey ?? string.Empty,
                ColorHex = colorHex ?? GetPresetColor(_sounds.Count),
                GradientHex = gradientHex,
                ImagePath = destImagePath,
                Category = string.IsNullOrWhiteSpace(category) ? "Custom" : category,
                Icon = icon ?? "volume-high",
                DurationSeconds = Math.Round(duration, 1),
                FileSizeBytes = fileSize,
                SortOrder = _sounds.Count
            };

            lock (_lock)
            {
                _sounds.Add(clip);
                SaveLibrary();
            }

            _audioEngine.LoadSound(clip);
            LibraryChanged?.Invoke(this, EventArgs.Empty);
            return clip;
        }

        public SoundClip AddSoundFromStream(Stream audioStream, string originalFileName, string title)
        {
            var id = Guid.NewGuid().ToString();
            var ext = Path.GetExtension(originalFileName);
            if (string.IsNullOrEmpty(ext)) ext = ".wav";
            var destFileName = $"{id}{ext}";
            var destPath = Path.Combine(_soundsDir, destFileName);

            using (var fileStream = File.Create(destPath))
            {
                audioStream.CopyTo(fileStream);
            }

            double duration = 0;
            try
            {
                using var reader = new AudioFileReader(destPath);
                duration = reader.TotalTime.TotalSeconds;
            }
            catch { }

            long fileSize = 0;
            try { fileSize = new FileInfo(destPath).Length; } catch { }

            var clip = new SoundClip
            {
                Id = id,
                Title = title,
                FileName = destFileName,
                FilePath = destPath,
                ColorHex = GetPresetColor(_sounds.Count),
                DurationSeconds = Math.Round(duration, 1),
                FileSizeBytes = fileSize,
                SortOrder = _sounds.Count
            };

            lock (_lock)
            {
                _sounds.Add(clip);
                SaveLibrary();
            }

            _audioEngine.LoadSound(clip);
            LibraryChanged?.Invoke(this, EventArgs.Empty);
            return clip;
        }

        public bool IsDuplicate(string title, string? fileName, long fileSizeBytes)
        {
            lock (_lock)
            {
                return _sounds.Any(s =>
                    // Match title and same non-zero file size
                    (string.Equals(s.Title, title, StringComparison.OrdinalIgnoreCase) && fileSizeBytes > 0 && s.FileSizeBytes == fileSizeBytes) ||
                    // Match filename and same non-zero file size
                    (!string.IsNullOrEmpty(fileName) && string.Equals(s.FileName, fileName, StringComparison.OrdinalIgnoreCase) && fileSizeBytes > 0 && s.FileSizeBytes == fileSizeBytes) ||
                    // Fallback match if sizes unknown
                    (fileSizeBytes == 0 && s.FileSizeBytes == 0 && string.Equals(s.Title, title, StringComparison.OrdinalIgnoreCase))
                );
            }
        }

        public SoundClip? GetSound(string id)
        {
            lock (_lock)
            {
                return _sounds.FirstOrDefault(s => s.Id == id);
            }
        }

        public bool RemoveSound(string id)
        {
            lock (_lock)
            {
                var clip = _sounds.FirstOrDefault(s => s.Id == id);
                if (clip == null) return false;

                _sounds.Remove(clip);
                _audioEngine.UnloadSound(id);

                try
                {
                    if (File.Exists(clip.FilePath))
                    {
                        File.Delete(clip.FilePath);
                    }
                    if (!string.IsNullOrEmpty(clip.ImagePath) && File.Exists(clip.ImagePath))
                    {
                        File.Delete(clip.ImagePath);
                    }
                }
                catch { }

                SaveLibrary();
            }

            LibraryChanged?.Invoke(this, EventArgs.Empty);
            return true;
        }

        public SoundClip? GetSoundById(string id)
        {
            lock (_lock)
            {
                return _sounds.FirstOrDefault(s => s.Id == id);
            }
        }

        public void UpdateSound(SoundClip updated)
        {
            lock (_lock)
            {
                var index = _sounds.FindIndex(s => s.Id == updated.Id);
                if (index >= 0)
                {
                    _sounds[index] = updated;
                    SaveLibrary();
                }
            }

            LibraryChanged?.Invoke(this, EventArgs.Empty);
        }

        private static string GetPresetColor(int index)
        {
            var colors = new[] { "#6366F1", "#EC4899", "#10B981", "#F59E0B", "#EF4444", "#06B6D4", "#8B5CF6", "#14B8A6" };
            return colors[index % colors.Length];
        }
    }
}
