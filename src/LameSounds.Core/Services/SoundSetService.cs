using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using LameSounds.Core.Models;

namespace LameSounds.Core.Services
{
    public class SoundSetService
    {
        private readonly string _storageDir;
        private readonly string _setsFile;
        private readonly List<SoundSet> _soundSets = new();
        private readonly object _lock = new();

        public IReadOnlyList<SoundSet> SoundSets
        {
            get
            {
                lock (_lock) return _soundSets.ToList();
            }
        }

        public event EventHandler? SetsChanged;

        public SoundSetService(string? customStorageDir = null)
        {
            _storageDir = customStorageDir ?? Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "LameSounds"
            );
            _setsFile = Path.Combine(_storageDir, "soundsets.json");

            Directory.CreateDirectory(_storageDir);
            LoadSets();
        }

        public void LoadSets()
        {
            lock (_lock)
            {
                _soundSets.Clear();

                if (File.Exists(_setsFile))
                {
                    try
                    {
                        var json = File.ReadAllText(_setsFile);
                        var loaded = JsonSerializer.Deserialize<List<SoundSet>>(json);
                        if (loaded != null && loaded.Count > 0)
                        {
                            _soundSets.AddRange(loaded);
                        }
                    }
                    catch (Exception ex)
                    {
                        System.Diagnostics.Debug.WriteLine($"[SoundSetService] Load error: {ex.Message}");
                    }
                }

                if (_soundSets.Count == 0)
                {
                    CreateDefaultSets();
                }
            }

            SetsChanged?.Invoke(this, EventArgs.Empty);
        }

        public void SaveSets()
        {
            lock (_lock)
            {
                try
                {
                    var json = JsonSerializer.Serialize(_soundSets, new JsonSerializerOptions { WriteIndented = true });
                    File.WriteAllText(_setsFile, json);
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[SoundSetService] Save error: {ex.Message}");
                }
            }

            SetsChanged?.Invoke(this, EventArgs.Empty);
        }

        public void AssignSlot(string setId, string slotId, string soundId, string label, string colorHex)
        {
            lock (_lock)
            {
                var set = _soundSets.FirstOrDefault(s => s.Id == setId);
                if (set != null)
                {
                    var slot = set.Buttons.FirstOrDefault(b => b.Id == slotId);
                    if (slot != null)
                    {
                        slot.SoundId = soundId;
                        slot.Label = label;
                        slot.ColorHex = colorHex;
                    }
                }
            }

            SaveSets();
        }

        private void CreateDefaultSets()
        {
            var defaultSet = new SoundSet
            {
                Id = "set_main",
                Name = "SOUND SET 1",
                Rows = 3,
                Columns = 3,
                Buttons = new List<SoundButtonConfig>()
            };

            for (int i = 0; i < 9; i++)
            {
                int r = i / 3;
                int c = i % 3;
                defaultSet.Buttons.Add(new SoundButtonConfig
                {
                    Id = $"slot_{i + 1}",
                    SoundId = string.Empty,
                    Label = $"+ Slot {i + 1}",
                    ColorHex = "#60CDFF",
                    Shape = ButtonShape.ROUNDED_RECTANGLE,
                    Row = r,
                    Column = c,
                    CornerRadius = 16,
                    FontSizeSp = 13,
                    IsBold = true
                });
            }
            _soundSets.Add(defaultSet);

            SaveSets();
        }
    }
}
