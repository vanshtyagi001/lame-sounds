using System;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using LameSounds.App.Converters;
using LameSounds.Core.Models;
using Microsoft.Win32;

namespace LameSounds.App.Views
{
    public partial class ImportSoundDialog : Window
    {
        private string? _selectedImagePath;
        private readonly SoundClip? _existingClip;
        private readonly string? _audioFilePath;
        private readonly MediaPlayer _mediaPlayer = new();
        private bool _isPlaying = false;

        public string SoundTitle => TxtTitle.Text.Trim();
        public string Category => (CmbCategory.SelectedItem as ComboBoxItem)?.Content?.ToString() ?? "Custom";
        public string ColorHex => TxtColorHex.Text.Trim();
        public string? GradientHex => ChkGradient.IsChecked == true ? TxtGradientHex.Text.Trim() : null;
        public string? ImagePath => _selectedImagePath;

        public ImportSoundDialog(string? audioFilePath = null, SoundClip? existingClip = null)
        {
            InitializeComponent();
            _audioFilePath = audioFilePath;
            _existingClip = existingClip;

            // Audio Player Event handlers
            _mediaPlayer.MediaEnded += (s, e) => Dispatcher.Invoke(StopAudioPlayback);
            _mediaPlayer.MediaFailed += (s, e) => Dispatcher.Invoke(() =>
            {
                StopAudioPlayback();
                TxtPlaybackStatus.Text = "Playback failed";
            });
            Closed += (s, e) =>
            {
                try
                {
                    _mediaPlayer.Stop();
                    _mediaPlayer.Close();
                }
                catch { }
            };

            if (existingClip != null)
            {
                // Edit mode
                Title = "Edit Sound - " + existingClip.Title;
                TxtHeader.Text = "Edit Sound Properties";
                TxtSubheader.Text = "Update title, category, color accents, gradients, or artwork icon.";
                TxtSaveBtn.Text = "Save Changes";

                TxtTitle.Text = existingClip.Title;

                if (!string.IsNullOrEmpty(existingClip.ColorHex))
                {
                    TxtColorHex.Text = existingClip.ColorHex;
                }

                if (!string.IsNullOrEmpty(existingClip.GradientHex))
                {
                    ChkGradient.IsChecked = true;
                    TxtGradientHex.Text = existingClip.GradientHex;
                }

                if (!string.IsNullOrEmpty(existingClip.ImagePath) && File.Exists(existingClip.ImagePath))
                {
                    SetSelectedImage(existingClip.ImagePath);
                }

                // Select matching category
                foreach (ComboBoxItem item in CmbCategory.Items)
                {
                    if (string.Equals(item.Content?.ToString(), existingClip.Category, StringComparison.OrdinalIgnoreCase))
                    {
                        CmbCategory.SelectedItem = item;
                        break;
                    }
                }

                PopulateMetadata(existingClip.FilePath, existingClip.DurationSeconds);
            }
            else
            {
                // Import mode
                if (!string.IsNullOrEmpty(audioFilePath))
                {
                    TxtTitle.Text = Path.GetFileNameWithoutExtension(audioFilePath);
                    PopulateMetadata(audioFilePath);
                }
                else
                {
                    TxtTitle.Text = "New Sound";
                }
            }

            UpdatePreview();
        }

        private void PopulateMetadata(string? filePath, double durationSeconds = 0)
        {
            if (!string.IsNullOrEmpty(filePath) && File.Exists(filePath))
            {
                var fi = new FileInfo(filePath);
                var sizeStr = FormatFileSize(fi.Length);
                var ext = fi.Extension.ToUpperInvariant().TrimStart('.');

                BadgeFileDetails.Visibility = Visibility.Visible;
                TxtBadgeFileName.Text = fi.Name;
                TxtBadgeFileSize.Text = sizeStr;

                TxtMetaSource.Text = fi.Name;
                TxtMetaSize.Text = sizeStr;
                TxtMetaFormat.Text = string.IsNullOrEmpty(ext) ? "AUDIO" : ext;

                try
                {
                    _mediaPlayer.Open(new Uri(filePath));
                }
                catch { }
            }
            else
            {
                BadgeFileDetails.Visibility = Visibility.Collapsed;
                TxtMetaSource.Text = "Direct Import";
                TxtMetaSize.Text = "Unknown";
                TxtMetaFormat.Text = "AUDIO";
                BtnTestAudio.IsEnabled = false;
                TxtPlaybackStatus.Text = "No file to preview";
            }
        }

        private static string FormatFileSize(long bytes)
        {
            if (bytes >= 1024 * 1024)
                return $"{(bytes / 1048576.0):0.0} MB";
            if (bytes >= 1024)
                return $"{(bytes / 1024.0):0.0} KB";
            return $"{bytes} B";
        }

        // --- Predefined Gradients & Colors ---
        private void ApplyPredefinedGradient_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string tag)
            {
                var parts = tag.Split('|');
                if (parts.Length >= 2)
                {
                    var startHex = parts[0];
                    var endHex = parts[1];

                    ChkGradient.IsChecked = true;
                    TxtColorHex.Text = startHex;
                    TxtGradientHex.Text = endHex;
                    UpdatePreview();
                }
            }
        }

        private void ColorPreset_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string hex)
            {
                TxtColorHex.Text = hex;
                ChkGradient.IsChecked = false;
                UpdatePreview();
            }
        }

        private void SwapColors_Click(object sender, RoutedEventArgs e)
        {
            var temp = TxtColorHex.Text;
            TxtColorHex.Text = TxtGradientHex.Text;
            TxtGradientHex.Text = temp;
            UpdatePreview();
        }

        private void ColorHex_Changed(object sender, TextChangedEventArgs e)
        {
            try
            {
                var color = (Color)ColorConverter.ConvertFromString(TxtColorHex.Text.Trim());
                PnlColorPreview.Background = new SolidColorBrush(color);
            }
            catch { }

            UpdatePreview();
        }

        private void GradientToggle_Changed(object sender, RoutedEventArgs e)
        {
            if (PnlGradientDetails != null)
            {
                PnlGradientDetails.Visibility = ChkGradient.IsChecked == true ? Visibility.Visible : Visibility.Collapsed;
            }
            UpdatePreview();
        }

        private void GradientHex_Changed(object sender, TextChangedEventArgs e)
        {
            try
            {
                var color = (Color)ColorConverter.ConvertFromString(TxtGradientHex.Text.Trim());
                PnlGradientPreview.Background = new SolidColorBrush(color);
            }
            catch { }

            UpdatePreview();
        }

        private void Field_Changed(object sender, EventArgs e)
        {
            UpdatePreview();
        }

        // --- Artwork / Image Picker ---
        private void BrowseImage_Click(object sender, RoutedEventArgs e)
        {
            var dlg = new OpenFileDialog
            {
                Title = "Select Image Icon for Sound",
                Filter = "Image Files (*.png;*.jpg;*.jpeg;*.webp;*.bmp)|*.png;*.jpg;*.jpeg;*.webp;*.bmp|All Files (*.*)|*.*"
            };

            if (dlg.ShowDialog(this) == true)
            {
                SetSelectedImage(dlg.FileName);
            }
        }

        private void RemoveImage_Click(object sender, RoutedEventArgs e)
        {
            _selectedImagePath = null;
            ImgPreview.Source = null;
            ImgPreview.Visibility = Visibility.Collapsed;
            TxtNoImage.Visibility = Visibility.Visible;
            BtnRemoveImage.Visibility = Visibility.Collapsed;

            PreviewThumbnailImg.Source = null;
            PreviewThumbnailBorder.Visibility = Visibility.Collapsed;
        }

        private void SetSelectedImage(string path)
        {
            try
            {
                var bmp = new BitmapImage();
                bmp.BeginInit();
                bmp.CacheOption = BitmapCacheOption.OnLoad;
                bmp.UriSource = new Uri(path, UriKind.RelativeOrAbsolute);
                bmp.EndInit();
                bmp.Freeze();

                _selectedImagePath = path;
                ImgPreview.Source = bmp;
                ImgPreview.Visibility = Visibility.Visible;
                TxtNoImage.Visibility = Visibility.Collapsed;
                BtnRemoveImage.Visibility = Visibility.Visible;

                PreviewThumbnailImg.Source = bmp;
                PreviewThumbnailBorder.Visibility = Visibility.Visible;
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Failed to load image: {ex.Message}", "Image Error", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }

        // --- Audio Test Player ---
        private void TestAudio_Click(object sender, RoutedEventArgs e)
        {
            if (_isPlaying)
            {
                StopAudioPlayback();
            }
            else
            {
                StartAudioPlayback();
            }
        }

        private void StartAudioPlayback()
        {
            try
            {
                _mediaPlayer.Position = TimeSpan.Zero;
                _mediaPlayer.Play();
                _isPlaying = true;
                TxtTestAudioLabel.Text = "⏹ Stop";
                TxtPlaybackStatus.Text = "Playing audio test...";
            }
            catch (Exception ex)
            {
                TxtPlaybackStatus.Text = "Error: " + ex.Message;
            }
        }

        private void StopAudioPlayback()
        {
            try
            {
                _mediaPlayer.Stop();
                _mediaPlayer.Position = TimeSpan.Zero;
            }
            catch { }

            _isPlaying = false;
            TxtTestAudioLabel.Text = "▶ Play";
            TxtPlaybackStatus.Text = "Ready to preview";
        }

        // --- Live Preview & Metadata Sync ---
        private void UpdatePreview()
        {
            if (PreviewTitle == null || CardAccentStrip == null) return;

            PreviewTitle.Text = string.IsNullOrWhiteSpace(TxtTitle.Text) ? "Sound Name" : TxtTitle.Text.Trim();
            var cat = (CmbCategory.SelectedItem as ComboBoxItem)?.Content?.ToString() ?? "Custom";
            var dur = _existingClip != null ? $"{_existingClip.DurationSeconds:0.0}s" : "0.0s";
            PreviewCategory.Text = $"{cat} • {dur}";

            var isGrad = ChkGradient.IsChecked == true;
            var gradHex = isGrad ? TxtGradientHex.Text.Trim() : null;
            var primaryHex = TxtColorHex.Text.Trim();

            CardAccentStrip.Background = SoundClipToBrushConverter.GetBrush(primaryHex, gradHex);

            if (TxtMetaAccent != null)
            {
                if (isGrad && !string.IsNullOrEmpty(gradHex))
                {
                    TxtMetaAccent.Text = $"Gradient ({primaryHex} → {gradHex})";
                }
                else
                {
                    TxtMetaAccent.Text = $"Solid ({primaryHex})";
                }
            }
        }

        // --- Dialog Commands ---
        private void Save_Click(object sender, RoutedEventArgs e)
        {
            if (string.IsNullOrWhiteSpace(TxtTitle.Text))
            {
                MessageBox.Show("Please enter a name for the sound clip.", "Validation Error", MessageBoxButton.OK, MessageBoxImage.Warning);
                TxtTitle.Focus();
                return;
            }

            StopAudioPlayback();
            DialogResult = true;
            Close();
        }

        private void Cancel_Click(object sender, RoutedEventArgs e)
        {
            StopAudioPlayback();
            DialogResult = false;
            Close();
        }
    }
}
