using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
using LameSounds.Core.Audio;
using LameSounds.Core.Models;
using LameSounds.Core.Network;
using LameSounds.Core.Services;
using Microsoft.Win32;

namespace LameSounds.App.ViewModels
{
    public class PagerDotItem : INotifyPropertyChanged
    {
        public int Index { get; set; }
        private bool _isActive;
        public bool IsActive
        {
            get => _isActive;
            set
            {
                if (_isActive != value)
                {
                    _isActive = value;
                    PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsActive)));
                }
            }
        }
        public event PropertyChangedEventHandler? PropertyChanged;
    }

    public class MainViewModel : INotifyPropertyChanged
    {
        private readonly AudioEngine _audioEngine;
        private readonly SoundLibraryService _soundLibrary;
        private readonly SettingsService _settingsService;
        private readonly SoundSetService _soundSetService;
        private readonly UdpTriggerListener _udpListener;
        private readonly HttpServer _httpServer;
        private readonly DiscoveryBeacon _discoveryBeacon;
        private readonly Dispatcher _dispatcher;

        // Sound Library & Collections
        public ObservableCollection<SoundClip> Sounds { get; } = new();
        public ObservableCollection<SoundClip> FilteredSounds { get; } = new();
        public ObservableCollection<string> Categories { get; } = new() { "ALL", "CS2", "MEMES", "REACTIONS", "CUSTOM" };
        public ObservableCollection<AudioDeviceInfo> PlaybackDevices { get; } = new();
        public ObservableCollection<AudioDeviceInfo> CaptureDevices { get; } = new();
        public ObservableCollection<string> ActivityLogs { get; } = new();

        // Sound Sets & Hardware Pager
        public ObservableCollection<SoundSet> SoundSets { get; } = new();
        public ObservableCollection<SoundButtonConfig> CurrentSlots { get; } = new();
        public ObservableCollection<PagerDotItem> PagerDots { get; } = new();

        private int _currentSetIndex = 0;
        private SoundButtonConfig? _assigningSlotConfig;

        // Modals state
        private bool _isLibraryOpen = false;
        private bool _isSearchOpen = false;
        private bool _isSettingsOpen = false;

        private string _searchQuery = string.Empty;
        private string _selectedCategory = "ALL";

        private AudioDeviceInfo? _selectedMonitorDevice;
        private AudioDeviceInfo? _selectedBroadcastDevice;
        private AudioDeviceInfo? _selectedMicDevice;
        private bool _isInitializingAudio;

        private float _monitorVolume = 1.0f;
        private float _broadcastVolume = 1.0f;
        private float _micVolume = 1.0f;
        private bool _micPassthroughEnabled = false;
        private bool _autoDuckingEnabled = true;

        private double _monitorPeak;
        private double _broadcastPeak;
        private double _micPeak;

        private string _ipAddress = "127.0.0.1";
        private int _httpPort = 50050;
        private int _udpPort = 50051;
        private ImageSource? _qrCodeImage;
        private bool _isVirtualCableInstalled;
        private string _statusText = "Ready";

        public event PropertyChangedEventHandler? PropertyChanged;

        public int CurrentSetIndex
        {
            get => _currentSetIndex;
            set
            {
                if (SetProperty(ref _currentSetIndex, value))
                {
                    OnPropertyChanged(nameof(CurrentSet));
                    OnPropertyChanged(nameof(CurrentSetName));
                    LoadCurrentSlots();
                    UpdatePagerDots();
                }
            }
        }

        public SoundSet? CurrentSet => SoundSets.Count > _currentSetIndex && _currentSetIndex >= 0 ? SoundSets[_currentSetIndex] : null;

        public string CurrentSetName => CurrentSet?.Name ?? "SOUND SET";

        public SoundButtonConfig? AssigningSlotConfig
        {
            get => _assigningSlotConfig;
            set
            {
                if (SetProperty(ref _assigningSlotConfig, value))
                {
                    OnPropertyChanged(nameof(AssigningSlotPrompt));
                    OnPropertyChanged(nameof(IsAssigningSlot));
                }
            }
        }

        public bool IsAssigningSlot => _assigningSlotConfig != null;

        public string AssigningSlotPrompt => _assigningSlotConfig != null
            ? $"Assigning to Slot ({_assigningSlotConfig.Row + 1}, {_assigningSlotConfig.Column + 1}) in {CurrentSetName}"
            : "AUDIO LIBRARY";

        public bool IsLibraryOpen
        {
            get => _isLibraryOpen;
            set
            {
                if (SetProperty(ref _isLibraryOpen, value))
                {
                    if (!value) AssigningSlotConfig = null;
                }
            }
        }

        public bool IsSearchOpen
        {
            get => _isSearchOpen;
            set => SetProperty(ref _isSearchOpen, value);
        }

        public bool IsSettingsOpen
        {
            get => _isSettingsOpen;
            set => SetProperty(ref _isSettingsOpen, value);
        }

        public string SearchQuery
        {
            get => _searchQuery;
            set
            {
                if (SetProperty(ref _searchQuery, value))
                {
                    ApplySoundFilter();
                }
            }
        }

        public string SelectedCategory
        {
            get => _selectedCategory;
            set
            {
                if (SetProperty(ref _selectedCategory, value))
                {
                    ApplySoundFilter();
                }
            }
        }

        public AudioDeviceInfo? SelectedMonitorDevice
        {
            get => _selectedMonitorDevice;
            set { if (SetProperty(ref _selectedMonitorDevice, value)) ApplyAudioRouting(); }
        }

        public AudioDeviceInfo? SelectedBroadcastDevice
        {
            get => _selectedBroadcastDevice;
            set { if (SetProperty(ref _selectedBroadcastDevice, value)) ApplyAudioRouting(); }
        }

        public AudioDeviceInfo? SelectedMicDevice
        {
            get => _selectedMicDevice;
            set { if (SetProperty(ref _selectedMicDevice, value)) ApplyAudioRouting(); }
        }

        public float MonitorVolume
        {
            get => _monitorVolume;
            set { if (SetProperty(ref _monitorVolume, value)) UpdateVolumes(); }
        }

        public float BroadcastVolume
        {
            get => _broadcastVolume;
            set { if (SetProperty(ref _broadcastVolume, value)) UpdateVolumes(); }
        }

        public float MicVolume
        {
            get => _micVolume;
            set { if (SetProperty(ref _micVolume, value)) UpdateVolumes(); }
        }

        public bool MicPassthroughEnabled
        {
            get => _micPassthroughEnabled;
            set { if (SetProperty(ref _micPassthroughEnabled, value)) ApplyAudioRouting(); }
        }

        public bool AutoDuckingEnabled
        {
            get => _autoDuckingEnabled;
            set
            {
                if (SetProperty(ref _autoDuckingEnabled, value))
                {
                    _audioEngine.Config.AutoDuckingEnabled = value;
                    _settingsService.Config.AutoDuckingEnabled = value;
                    _settingsService.SaveConfig();
                }
            }
        }

        public double MonitorPeak
        {
            get => _monitorPeak;
            set => SetProperty(ref _monitorPeak, value);
        }

        public double BroadcastPeak
        {
            get => _broadcastPeak;
            set => SetProperty(ref _broadcastPeak, value);
        }

        public double MicPeak
        {
            get => _micPeak;
            set => SetProperty(ref _micPeak, value);
        }

        public string IpAddress
        {
            get => _ipAddress;
            set => SetProperty(ref _ipAddress, value);
        }

        public int HttpPort
        {
            get => _httpPort;
            set => SetProperty(ref _httpPort, value);
        }

        public int UdpPort
        {
            get => _udpPort;
            set => SetProperty(ref _udpPort, value);
        }

        public ImageSource? QrCodeImage
        {
            get => _qrCodeImage;
            set => SetProperty(ref _qrCodeImage, value);
        }

        public bool IsVirtualCableInstalled
        {
            get => _isVirtualCableInstalled;
            set => SetProperty(ref _isVirtualCableInstalled, value);
        }

        public string StatusText
        {
            get => _statusText;
            set => SetProperty(ref _statusText, value);
        }

        public MainViewModel()
        {
            _dispatcher = Application.Current?.Dispatcher ?? Dispatcher.CurrentDispatcher;

            var deviceMgr = new DeviceManager();
            _audioEngine = new AudioEngine(deviceMgr);
            _soundLibrary = new SoundLibraryService(_audioEngine);
            _settingsService = new SettingsService();
            _soundSetService = new SoundSetService();
            _udpListener = new UdpTriggerListener(_audioEngine);
            _httpServer = new HttpServer(_audioEngine, _soundLibrary, _settingsService);
            _discoveryBeacon = new DiscoveryBeacon();

            InitializeServices();
        }

        private void InitializeServices()
        {
            _isInitializingAudio = true;
            var config = _settingsService.Config;

            _monitorVolume = config.MonitorVolume;
            _broadcastVolume = config.BroadcastVolume;
            _micVolume = config.MicVolume;
            _micPassthroughEnabled = config.MicPassthroughEnabled;
            _autoDuckingEnabled = config.AutoDuckingEnabled;
            _httpPort = config.HttpPort;
            _udpPort = config.UdpPort;

            _ipAddress = NetworkManager.GetLocalIpAddress();

            // Refresh & validate audio devices first
            RefreshAudioDevices();

            // Initialize AudioEngine with active valid devices
            _isInitializingAudio = false;
            ApplyAudioRouting();

            // Refresh Sounds
            RefreshSounds();
            _soundLibrary.LibraryChanged += (s, e) => _dispatcher.Invoke(RefreshSounds);

            // Refresh Sound Sets
            RefreshSoundSets();
            _soundSetService.SetsChanged += (s, e) => _dispatcher.Invoke(RefreshSoundSets);

            // Audio VU Meters
            _audioEngine.VUMeterUpdated += (s, e) =>
            {
                _dispatcher.InvokeAsync(() =>
                {
                    MonitorPeak = Math.Clamp(e.MonitorPeak * 100.0, 0, 100);
                    BroadcastPeak = Math.Clamp(e.BroadcastPeak * 100.0, 0, 100);
                    MicPeak = Math.Clamp(e.MicPeak * 100.0, 0, 100);
                });
            };

            // Start Networking
            _udpListener.TriggerReceived += (s, e) =>
            {
                _dispatcher.InvokeAsync(() =>
                {
                    LogActivity($"[UDP] Triggered sound '{e.SoundId}' from {e.RemoteEndpoint}");
                });
            };

            _udpListener.Start(_udpPort);
            _httpServer.Start(_httpPort);
            _httpServer.PhoneRegistered += (s, e) =>
            {
                _dispatcher.InvokeAsync(() =>
                {
                    LogActivity($"Phone connected from {_httpServer.LastPhoneIp} ({_httpServer.LastPhoneSoundsCount} sound(s))");
                });
            };
            _discoveryBeacon.Start(_ipAddress, _httpPort, _udpPort);

            // Generate QR Code
            GenerateQr();

            LogActivity($"LameSounds initialized on {_ipAddress} (UDP: {_udpPort}, HTTP: {_httpPort})");
        }

        public void RefreshSoundSets()
        {
            SoundSets.Clear();
            foreach (var set in _soundSetService.SoundSets)
            {
                SoundSets.Add(set);
            }

            if (_currentSetIndex >= SoundSets.Count)
            {
                _currentSetIndex = Math.Max(0, SoundSets.Count - 1);
            }

            OnPropertyChanged(nameof(CurrentSet));
            OnPropertyChanged(nameof(CurrentSetName));
            LoadCurrentSlots();
            UpdatePagerDots();
        }

        public void LoadCurrentSlots()
        {
            CurrentSlots.Clear();
            if (CurrentSet != null)
            {
                foreach (var btn in CurrentSet.Buttons)
                {
                    CurrentSlots.Add(btn);
                }
            }
        }

        private void UpdatePagerDots()
        {
            PagerDots.Clear();
            for (int i = 0; i < SoundSets.Count; i++)
            {
                PagerDots.Add(new PagerDotItem
                {
                    Index = i,
                    IsActive = i == _currentSetIndex
                });
            }
        }

        public void NextSet()
        {
            if (SoundSets.Count > 0)
            {
                CurrentSetIndex = (CurrentSetIndex + 1) % SoundSets.Count;
            }
        }

        public void PreviousSet()
        {
            if (SoundSets.Count > 0)
            {
                CurrentSetIndex = (CurrentSetIndex - 1 + SoundSets.Count) % SoundSets.Count;
            }
        }

        public void SelectSet(int index)
        {
            if (index >= 0 && index < SoundSets.Count)
            {
                CurrentSetIndex = index;
            }
        }

        public void TriggerSlot(SoundButtonConfig slot)
        {
            if (slot.IsEmpty)
            {
                OpenLibraryForSlot(slot);
            }
            else
            {
                _audioEngine.PlaySound(slot.SoundId, 1.0f);
                LogActivity($"Triggered '{slot.Label}' (Slot {slot.Row + 1},{slot.Column + 1})");
            }
        }

        public void OpenLibraryForSlot(SoundButtonConfig slot)
        {
            AssigningSlotConfig = slot;
            IsLibraryOpen = true;
        }

        public void AssignSoundToSlot(SoundClip clip)
        {
            if (AssigningSlotConfig != null && CurrentSet != null)
            {
                _soundSetService.AssignSlot(CurrentSet.Id, AssigningSlotConfig.Id, clip.Id, clip.Title, clip.ColorHex);
                AssigningSlotConfig = null;
                IsLibraryOpen = false;
                LoadCurrentSlots();
                LogActivity($"Assigned '{clip.Title}' to slot");
            }
            else
            {
                PlaySound(clip);
            }
        }

        public void ClearSlot(SoundButtonConfig slot)
        {
            if (CurrentSet != null)
            {
                _soundSetService.AssignSlot(CurrentSet.Id, slot.Id, "", $"+ Slot {slot.Row * 3 + slot.Column + 1}", "#6366F1");
                LoadCurrentSlots();
                LogActivity($"Cleared slot {slot.Row + 1},{slot.Column + 1}");
            }
        }

        public void RefreshAudioDevices()
        {
            PlaybackDevices.Clear();
            CaptureDevices.Clear();

            var playback = _audioEngine.DeviceManager.GetPlaybackDevices();
            var capture = _audioEngine.DeviceManager.GetCaptureDevices();

            foreach (var p in playback) PlaybackDevices.Add(p);
            foreach (var c in capture) CaptureDevices.Add(c);

            IsVirtualCableInstalled = _audioEngine.DeviceManager.IsVirtualCableInstalled();

            // Restore Selected Devices
            var config = _settingsService.Config;
            SelectedMonitorDevice = PlaybackDevices.FirstOrDefault(d => d.Id == config.MonitorDeviceId) ??
                                   PlaybackDevices.FirstOrDefault(d => d.IsDefault) ??
                                   PlaybackDevices.FirstOrDefault();

            SelectedBroadcastDevice = PlaybackDevices.FirstOrDefault(d => d.Id == config.BroadcastDeviceId) ??
                                     PlaybackDevices.FirstOrDefault(d => d.IsVirtualCable);

            SelectedMicDevice = CaptureDevices.FirstOrDefault(d => d.Id == config.MicDeviceId) ??
                                CaptureDevices.FirstOrDefault(d => d.IsDefault) ??
                                CaptureDevices.FirstOrDefault();
        }

        public void RefreshSounds()
        {
            Sounds.Clear();
            foreach (var sound in _soundLibrary.Sounds)
            {
                Sounds.Add(sound);
            }
            ApplySoundFilter();
        }

        public void ApplySoundFilter()
        {
            FilteredSounds.Clear();
            var query = _searchQuery?.Trim() ?? string.Empty;
            var cat = _selectedCategory ?? "ALL";

            var matches = Sounds.Where(s =>
            {
                bool catMatch = cat == "ALL" ||
                    (cat == "CS2" && s.Category.Equals("CS2", StringComparison.OrdinalIgnoreCase)) ||
                    (cat == "MEMES" && s.Category.Equals("Memes", StringComparison.OrdinalIgnoreCase)) ||
                    (cat == "REACTIONS" && s.Category.Equals("Reactions", StringComparison.OrdinalIgnoreCase)) ||
                    (cat == "CUSTOM" && (s.Category.Equals("Custom", StringComparison.OrdinalIgnoreCase) || s.Id.StartsWith("custom_")));

                if (!catMatch && cat != "ALL")
                {
                    catMatch = s.Category.Equals(cat, StringComparison.OrdinalIgnoreCase);
                }

                if (!catMatch) return false;

                if (string.IsNullOrEmpty(query)) return true;

                return s.Title.Contains(query, StringComparison.OrdinalIgnoreCase) ||
                       s.Category.Contains(query, StringComparison.OrdinalIgnoreCase);
            });

            foreach (var sound in matches)
            {
                FilteredSounds.Add(sound);
            }
        }

        private void GenerateQr()
        {
            try
            {
                var bytes = NetworkManager.GenerateQrCodePngBytes(_ipAddress, _httpPort, _udpPort);
                var image = new BitmapImage();
                using (var mem = new MemoryStream(bytes))
                {
                    image.BeginInit();
                    image.CacheOption = BitmapCacheOption.OnLoad;
                    image.StreamSource = mem;
                    image.EndInit();
                }
                image.Freeze();
                QrCodeImage = image;
            }
            catch (Exception ex)
            {
                LogActivity($"QR Generation error: {ex.Message}");
            }
        }

        public void PlaySound(SoundClip clip)
        {
            _audioEngine.PlaySound(clip.Id, clip.Volume);
            LogActivity($"Playing '{clip.Title}'");
        }

        public void StopSound(SoundClip clip)
        {
            _audioEngine.StopSound(clip.Id);
            LogActivity($"Stopped '{clip.Title}'");
        }

        public void StopAll()
        {
            _audioEngine.StopAll();
            LogActivity("Stopped all playing sounds");
        }

        public SoundClip AddCustomSound(
            string audioFilePath,
            string title,
            string? colorHex = null,
            string? icon = null,
            string? category = null,
            string? gradientHex = null,
            string? imageFilePath = null)
        {
            var added = _soundLibrary.AddSound(audioFilePath, title, null, colorHex, icon, category, gradientHex, imageFilePath);
            LogActivity($"Imported sound '{added.Title}'");
            return added;
        }

        public void UpdateExistingSound(
            SoundClip clip,
            string title,
            string? colorHex,
            string? category,
            string? gradientHex,
            string? imageFilePath)
        {
            clip.Title = title;
            clip.ColorHex = colorHex ?? "#60CDFF";
            clip.Category = category ?? "Custom";
            clip.GradientHex = gradientHex;

            if (!string.IsNullOrWhiteSpace(imageFilePath) && File.Exists(imageFilePath))
            {
                clip.ImagePath = imageFilePath;
            }
            else if (string.IsNullOrEmpty(imageFilePath))
            {
                clip.ImagePath = null;
            }

            _soundLibrary.UpdateSound(clip);
            ApplySoundFilter();
            LogActivity($"Updated sound '{clip.Title}'");
        }

        private static readonly (string primary, string secondary)[] DefaultGradientPresets = new[]
        {
            ("#6366F1", "#A855F7"), // Cyber Indigo
            ("#EC4899", "#8B5CF6"), // Synth Neon
            ("#3B82F6", "#06B6D4"), // Deep Ocean
            ("#10B981", "#059669"), // Emerald Pulse
            ("#F59E0B", "#EF4444"), // Amber Flare
            ("#8B5CF6", "#EC4899"), // Electric Violet
            ("#06B6D4", "#3B82F6"), // Arctic Breeze
            ("#F43F5E", "#FB923C"), // Sunset Coral
            ("#14B8A6", "#10B981"), // Mint Glow
            ("#6366F1", "#3B82F6"), // Royal Azure
            ("#D946EF", "#8B5CF6"), // Cosmic Nebula
            ("#64748B", "#475569"), // Gunmetal Slate
            ("#84CC16", "#10B981"), // Lime Pulse
            ("#F97316", "#DC2626"), // Solar Flare
            ("#0284C7", "#2563EB"), // Deep Sapphire
            ("#7C3AED", "#4F46E5"), // Ultraviolet
            ("#DB2777", "#9333EA"), // Fuchsia Dream
            ("#0D9488", "#0284C7"), // Cyan Wave
            ("#E11D48", "#C026D3"), // Velvet Berry
            ("#4338CA", "#3B82F6")  // Midnight Cobalt
        };

        public void AddNewSound()
        {
            var dlg = new OpenFileDialog
            {
                Title = "Select Audio Clip(s)",
                Filter = "Audio Files (*.mp3;*.wav;*.ogg;*.flac;*.aac;*.m4a)|*.mp3;*.wav;*.ogg;*.flac;*.aac;*.m4a|All Files (*.*)|*.*",
                Multiselect = true
            };

            if (dlg.ShowDialog() == true && dlg.FileNames.Length > 0)
            {
                if (dlg.FileNames.Length == 1)
                {
                    var file = dlg.FileName;
                    var importDlg = new Views.ImportSoundDialog(file)
                    {
                        Owner = Application.Current?.MainWindow
                    };

                    if (importDlg.ShowDialog() == true)
                    {
                        AddCustomSound(
                            file,
                            importDlg.SoundTitle,
                            importDlg.ColorHex,
                            null,
                            importDlg.Category,
                            importDlg.GradientHex,
                            importDlg.ImagePath);
                    }
                }
                else
                {
                    BulkImportSounds(dlg.FileNames);
                }
            }
        }

        public int BulkImportSounds(IEnumerable<string>? filesToImport = null)
        {
            var list = filesToImport?.ToList();
            if (list == null || list.Count == 0)
            {
                var dlg = new OpenFileDialog
                {
                    Title = "Select Audio Files for Bulk Import",
                    Filter = "Audio Files (*.mp3;*.wav;*.ogg;*.flac;*.aac;*.m4a)|*.mp3;*.wav;*.ogg;*.flac;*.aac;*.m4a|All Files (*.*)|*.*",
                    Multiselect = true
                };

                if (dlg.ShowDialog() != true || dlg.FileNames.Length == 0)
                {
                    return 0;
                }

                list = dlg.FileNames.ToList();
            }

            int importedCount = 0;
            int skippedCount = 0;
            var validExts = new HashSet<string>(StringComparer.OrdinalIgnoreCase) { ".mp3", ".wav", ".ogg", ".flac", ".aac", ".m4a" };

            foreach (var file in list)
            {
                if (!File.Exists(file)) continue;
                var ext = Path.GetExtension(file);
                if (!validExts.Contains(ext)) continue;

                var rawName = Path.GetFileNameWithoutExtension(file);
                var title = string.IsNullOrWhiteSpace(rawName) ? "Sound" : rawName.Trim();

                // Check for duplicates in the current library by file size and title
                var fileLen = new FileInfo(file).Length;
                var isDuplicate = _soundLibrary.Sounds.Any(s =>
                    (s.Title.Equals(title, StringComparison.OrdinalIgnoreCase) && s.FileSizeBytes == fileLen) ||
                    (s.Title.Equals(title, StringComparison.OrdinalIgnoreCase) && s.FileSizeBytes == 0));

                if (isDuplicate)
                {
                    skippedCount++;
                    continue;
                }

                // Randomly select a gradient preset from the curated palette
                var randomIdx = Random.Shared.Next(DefaultGradientPresets.Length);
                var preset = DefaultGradientPresets[randomIdx];

                _soundLibrary.AddSound(
                    sourceFilePath: file,
                    title: title,
                    hotkey: null,
                    colorHex: preset.primary,
                    icon: "volume-high",
                    category: "Custom",
                    gradientHex: preset.secondary,
                    imageFilePath: null);

                importedCount++;
            }

            ApplySoundFilter();

            if (importedCount > 0)
            {
                var msg = skippedCount > 0
                    ? $"✓ Bulk imported {importedCount} sound(s) ({skippedCount} duplicate(s) skipped)"
                    : $"✓ Bulk imported {importedCount} sound(s) successfully";
                LogActivity(msg);
                MessageBox.Show(msg, "Bulk Import Complete", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            else if (skippedCount > 0)
            {
                var msg = $"All {skippedCount} selected file(s) are already in your soundboard.";
                LogActivity(msg);
                MessageBox.Show(msg, "Bulk Import", MessageBoxButton.OK, MessageBoxImage.Information);
            }

            return importedCount;
        }

        public void RemoveSound(SoundClip clip)
        {
            if (MessageBox.Show($"Are you sure you want to delete '{clip.Title}' from this PC?\n(This will delete the sound locally on this PC only; it will NOT be deleted from your phone).", "Delete Sound (PC Only)",
                MessageBoxButton.YesNo, MessageBoxImage.Question) == MessageBoxResult.Yes)
            {
                _soundLibrary.RemoveSound(clip.Id);
                LogActivity($"Deleted sound '{clip.Title}' from PC");
            }
        }

        private bool _isImportingFromPhone;
        public bool IsImportingFromPhone
        {
            get => _isImportingFromPhone;
            set => SetProperty(ref _isImportingFromPhone, value);
        }

        public async Task ImportAllFromPhoneAsync()
        {
            if (_isImportingFromPhone) return;

            var phoneIp = _httpServer.LastPhoneIp;
            var phonePort = _httpServer.LastPhonePort;

            if (string.IsNullOrEmpty(phoneIp))
            {
                MessageBox.Show(
                    "No phone connection detected yet.\n\nMake sure the LameSounds mobile app is open and connected to your PC's IP on the same Wi-Fi network, or tap 'Send to PC' directly in the mobile app library.",
                    "Phone Not Connected", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            IsImportingFromPhone = true;
            LogActivity($"Connecting to phone at {phoneIp}:{phonePort}...");

            try
            {
                using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(6) };
                var response = await http.GetAsync($"http://{phoneIp}:{phonePort}/api/phone/sounds");
                if (!response.IsSuccessStatusCode)
                {
                    LogActivity($"Failed to reach phone sound library (HTTP {response.StatusCode})");
                    MessageBox.Show($"Could not reach phone audio service at {phoneIp}:{phonePort}.\nEnsure the LameSounds mobile app is open.", "Connection Error", MessageBoxButton.OK, MessageBoxImage.Warning);
                    return;
                }

                var jsonStr = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(jsonStr);
                if (doc.RootElement.ValueKind != JsonValueKind.Array)
                {
                    LogActivity("Invalid sounds list received from phone.");
                    return;
                }

                int imported = 0;
                int duplicates = 0;

                foreach (var el in doc.RootElement.EnumerateArray())
                {
                    var id = el.TryGetProperty("id", out var idProp) ? idProp.GetString() : null;
                    var title = el.TryGetProperty("title", out var titleProp) ? titleProp.GetString() : "Phone Sound";
                    var fileName = el.TryGetProperty("fileName", out var fnProp) ? fnProp.GetString() : "sound.mp3";
                    var fileSizeBytes = el.TryGetProperty("fileSizeBytes", out var fsbProp) ? fsbProp.GetInt64() : 0;

                    if (string.IsNullOrEmpty(id) || string.IsNullOrEmpty(title)) continue;

                    // Deduplication check: title + size or filename + size
                    if (_soundLibrary.IsDuplicate(title, fileName, fileSizeBytes))
                    {
                        duplicates++;
                        continue;
                    }

                    // Download the audio file from phone
                    try
                    {
                        var audioBytes = await http.GetByteArrayAsync($"http://{phoneIp}:{phonePort}/api/phone/sounds/{id}/file");
                        var ext = Path.GetExtension(fileName);
                        if (string.IsNullOrEmpty(ext)) ext = ".mp3";

                        var tempFile = Path.Combine(Path.GetTempPath(), $"phone_{Guid.NewGuid():N}{ext}");
                        await File.WriteAllBytesAsync(tempFile, audioBytes);

                        _soundLibrary.AddSound(tempFile, title, category: "Phone");
                        try { File.Delete(tempFile); } catch { }
                        imported++;
                    }
                    catch (Exception dlEx)
                    {
                        LogActivity($"Error importing '{title}': {dlEx.Message}");
                    }
                }

                var msg = $"Import complete: {imported} sound(s) imported from Phone ({duplicates} duplicate(s) skipped).";
                LogActivity(msg);
                MessageBox.Show(msg, "Phone Import Complete", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            catch (Exception ex)
            {
                LogActivity($"Import from phone failed: {ex.Message}");
                MessageBox.Show($"Could not import sounds from phone:\n{ex.Message}\n\nTip: You can also use 'Send to PC' directly in the mobile app library.", "Import Failed", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
            finally
            {
                IsImportingFromPhone = false;
            }
        }

        public void SendTestTrigger()
        {
            var firstSound = Sounds.FirstOrDefault();
            if (firstSound != null)
            {
                try
                {
                    using var client = new System.Net.Sockets.UdpClient();
                    var msg = $"{{\"type\":\"play\",\"soundId\":\"{firstSound.Id}\",\"volume\":1.0}}";
                    var bytes = Encoding.UTF8.GetBytes(msg);
                    client.Send(bytes, bytes.Length, "127.0.0.1", _udpPort);
                    LogActivity($"Sent test UDP trigger for '{firstSound.Title}'");
                }
                catch (Exception ex)
                {
                    LogActivity($"Test UDP trigger failed: {ex.Message}");
                }
            }
        }

        public void OpenVirtualCableGuide()
        {
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = "https://vb-audio.com/Cable/",
                    UseShellExecute = true
                });
            }
            catch { }
        }

        private void ApplyAudioRouting()
        {
            if (_isInitializingAudio) return;

            var monId = _selectedMonitorDevice?.Id;
            var broadId = _selectedBroadcastDevice?.Id;
            var micId = _selectedMicDevice?.Id;

            _audioEngine.UpdateDevices(monId, broadId, micId, _micPassthroughEnabled);

            var config = _settingsService.Config;
            config.MonitorDeviceId = monId ?? "";
            config.BroadcastDeviceId = broadId ?? "";
            config.MicDeviceId = micId ?? "";
            config.MicPassthroughEnabled = _micPassthroughEnabled;
            _settingsService.SaveConfig();

            LogActivity($"Audio routing updated: Monitor='{_selectedMonitorDevice?.Name}', Broadcast='{_selectedBroadcastDevice?.Name}'");
        }

        private void UpdateVolumes()
        {
            _audioEngine.UpdateVolumes(_monitorVolume, _broadcastVolume, _micVolume);

            var config = _settingsService.Config;
            config.MonitorVolume = _monitorVolume;
            config.BroadcastVolume = _broadcastVolume;
            config.MicVolume = _micVolume;
            _settingsService.SaveConfig();
        }

        private void LogActivity(string msg)
        {
            var entry = $"[{DateTime.Now:HH:mm:ss}] {msg}";
            ActivityLogs.Insert(0, entry);
            if (ActivityLogs.Count > 50) ActivityLogs.RemoveAt(ActivityLogs.Count - 1);
            StatusText = msg;
        }

        public void Cleanup()
        {
            _udpListener.Dispose();
            _httpServer.Dispose();
            _discoveryBeacon.Dispose();
            _audioEngine.Dispose();
        }

        protected bool SetProperty<T>(ref T storage, T value, [CallerMemberName] string? propertyName = null)
        {
            if (Equals(storage, value)) return false;
            storage = value;
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
            return true;
        }

        protected void OnPropertyChanged([CallerMemberName] string? propertyName = null)
        {
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
        }
    }
}
