using System;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using LameSounds.App.ViewModels;
using LameSounds.Core.Models;

namespace LameSounds.App
{
    public partial class MainWindow : Window
    {
        public MainWindow()
        {
            InitializeComponent();
            Closing += MainWindow_Closing;
            LoadWindowIcon();
        }

        private void LoadWindowIcon()
        {
            try
            {
                var iconPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Resources", "app_logo.png");
                if (File.Exists(iconPath))
                {
                    Icon = new System.Windows.Media.Imaging.BitmapImage(new Uri(iconPath, UriKind.Absolute));
                }
                else
                {
                    var uri = new Uri("pack://application:,,,/Resources/app_logo.png", UriKind.RelativeOrAbsolute);
                    Icon = new System.Windows.Media.Imaging.BitmapImage(uri);
                }
            }
            catch
            {
                // Graceful fallback to avoid any startup crash
            }
        }

        private void MainWindow_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
        {
            ViewModel?.Cleanup();
        }

        // --- Sidebar Navigation ---
        private void Nav_Checked(object sender, RoutedEventArgs e)
        {
            if (PageSoundboard == null || PageLibrary == null || PageMixer == null || PageCompanion == null || TxtPageTitle == null)
                return;

            PageSoundboard.Visibility = Visibility.Collapsed;
            PageLibrary.Visibility = Visibility.Collapsed;
            PageMixer.Visibility = Visibility.Collapsed;
            PageCompanion.Visibility = Visibility.Collapsed;

            if (TabSoundboard.IsChecked == true)
            {
                PageSoundboard.Visibility = Visibility.Visible;
                TxtPageTitle.Text = "Soundboard";
            }
            else if (TabLibrary.IsChecked == true)
            {
                PageLibrary.Visibility = Visibility.Visible;
                TxtPageTitle.Text = "Sound Library";
            }
            else if (TabMixer.IsChecked == true)
            {
                PageMixer.Visibility = Visibility.Visible;
                TxtPageTitle.Text = "Audio Mixer";
            }
            else if (TabCompanion.IsChecked == true)
            {
                PageCompanion.Visibility = Visibility.Visible;
                TxtPageTitle.Text = "Mobile Companion";
            }
        }

        // --- Command Bar Actions ---
        private void AddSound_Click(object sender, RoutedEventArgs e)
        {
            ViewModel?.AddNewSound();
        }

        private void BulkImport_Click(object sender, RoutedEventArgs e)
        {
            ViewModel?.BulkImportSounds();
        }

        private async void ImportFromPhone_Click(object sender, RoutedEventArgs e)
        {
            if (ViewModel != null)
            {
                await ViewModel.ImportAllFromPhoneAsync();
            }
        }

        private void StopAll_Click(object sender, RoutedEventArgs e)
        {
            ViewModel?.StopAll();
        }

        // --- Filter Bar ---
        private void CategoryFilter_Checked(object sender, RoutedEventArgs e)
        {
            if (sender is RadioButton rb && rb.Tag is string category && ViewModel != null)
            {
                ViewModel.SelectedCategory = category;
            }
        }

        // --- Sound Card Actions ---
        private void SoundCard_PlayClick(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton == MouseButton.Left && sender is FrameworkElement elem && elem.DataContext is SoundClip clip)
            {
                ViewModel?.PlaySound(clip);
            }
        }

        private void PlayButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is SoundClip clip)
            {
                ViewModel?.PlaySound(clip);
                e.Handled = true;
            }
        }

        private void StopButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is SoundClip clip)
            {
                ViewModel?.StopSound(clip);
                e.Handled = true;
            }
        }

        private void DeleteButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is SoundClip clip)
            {
                ViewModel?.RemoveSound(clip);
                e.Handled = true;
            }
        }

        // --- Context Menu Actions ---
        private void PlayContext_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem item && item.DataContext is SoundClip clip)
            {
                ViewModel?.PlaySound(clip);
            }
        }

        private void StopContext_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem item && item.DataContext is SoundClip clip)
            {
                ViewModel?.StopSound(clip);
            }
        }

        private void DeleteContext_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem item && item.DataContext is SoundClip clip)
            {
                ViewModel?.RemoveSound(clip);
            }
        }

        private void EditContext_Click(object sender, RoutedEventArgs e)
        {
            if (sender is MenuItem item && item.DataContext is SoundClip clip)
            {
                OpenEditDialog(clip);
            }
        }

        private void EditButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is SoundClip clip)
            {
                OpenEditDialog(clip);
                e.Handled = true;
            }
        }

        private void OpenEditDialog(SoundClip clip)
        {
            var dlg = new Views.ImportSoundDialog(null, clip)
            {
                Owner = this
            };

            if (dlg.ShowDialog() == true && ViewModel != null)
            {
                ViewModel.UpdateExistingSound(
                    clip,
                    dlg.SoundTitle,
                    dlg.ColorHex,
                    dlg.Category,
                    dlg.GradientHex,
                    dlg.ImagePath);
            }
        }

        // --- Driver / Hardware Badge Actions ---
        private void VirtualCableBadge_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (ViewModel?.IsVirtualCableInstalled == false)
            {
                ViewModel?.OpenVirtualCableGuide();
            }
            else
            {
                TabMixer.IsChecked = true;
            }
        }

        private void TestTrigger_Click(object sender, RoutedEventArgs e)
        {
            ViewModel?.SendTestTrigger();
        }

        // --- Drag & Drop Audio Files ---
        private void Window_Drop(object sender, DragEventArgs e)
        {
            if (e.Data.GetDataPresent(DataFormats.FileDrop))
            {
                var files = (string[])e.Data.GetData(DataFormats.FileDrop);
                if (files == null || files.Length == 0) return;

                var audioFiles = files
                    .Where(f =>
                    {
                        var ext = Path.GetExtension(f).ToLowerInvariant();
                        return ext is ".mp3" or ".wav" or ".ogg" or ".flac" or ".aac" or ".m4a";
                    })
                    .ToList();

                if (audioFiles.Count == 0) return;

                if (audioFiles.Count > 1)
                {
                    // Multiple files dropped -> Automatically bulk import them named by file name
                    ViewModel?.BulkImportSounds(audioFiles);
                }
                else
                {
                    // Single file dropped -> Open the customization dialog
                    var file = audioFiles[0];
                    var dlg = new Views.ImportSoundDialog(file)
                    {
                        Owner = this
                    };

                    if (dlg.ShowDialog() == true && ViewModel != null)
                    {
                        ViewModel.AddCustomSound(
                            file,
                            dlg.SoundTitle,
                            dlg.ColorHex,
                            null,
                            dlg.Category,
                            dlg.GradientHex,
                            dlg.ImagePath);
                    }
                }
            }
        }
    }
}