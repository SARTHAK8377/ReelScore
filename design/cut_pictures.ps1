# Cuts the Canva Dream Lab pictures in design/pictures/raw into transparent PNGs for the app.
# White background is removed by flood fill from the edges (so white inside an object stays),
# sheets with several objects are split into separate pictures, and each is scaled to at most
# 600 px on its longest side.
#   powershell -File design\cut_pictures.ps1
param(
    [string]$Raw = (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'pictures\raw'),
    [string]$Out = (Join-Path (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)) 'app\src\main\res\drawable-nodpi')
)

# Raw file -> the names of the objects in it, in reading order (top to bottom, then left to right).
$sheets = [ordered]@{
    'phone'          = @('phone')
    'bat'            = @('bat')
    'door'           = @('door')
    'car'            = @('car')
    'sheet_vehicles' = @('auto_rickshaw', 'bus', 'train_coach')
    'sheet_courts'   = @('cricket_pitch', 'tennis_court', 'basketball_court')
    'sheet_grid'     = @('pool', 'football_field', 'elephant', 'blue_whale')
}

Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class Cutter {
    const int Block = 10;      // objects closer than this many pixels count as one
    const int WhiteMin = 232;  // background: every channel at least this bright...
    const int WhiteSpread = 22; // ...and nearly grey

    static bool IsWhite(byte[] p, int i) {
        int b = p[i], g = p[i + 1], r = p[i + 2];
        int mn = Math.Min(r, Math.Min(g, b)), mx = Math.Max(r, Math.Max(g, b));
        return mn >= WhiteMin && mx - mn <= WhiteSpread;
    }

    public static int Cut(string src, string[] names, string outDir, int maxSide) {
        using (var raw = new Bitmap(src))
        using (var bmp = raw.Clone(new Rectangle(0, 0, raw.Width, raw.Height), PixelFormat.Format32bppArgb)) {
            int w = bmp.Width, h = bmp.Height;
            var data = bmp.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
            var px = new byte[data.Stride * h];
            Marshal.Copy(data.Scan0, px, 0, px.Length);
            int stride = data.Stride;

            // 1. Background: near-white pixels reachable from the border.
            var bg = new bool[w * h];
            var queue = new Queue<int>();
            Action<int, int> seed = (x, y) => { int k = y * w + x; if (!bg[k] && IsWhite(px, y * stride + x * 4)) { bg[k] = true; queue.Enqueue(k); } };
            for (int x = 0; x < w; x++) { seed(x, 0); seed(x, h - 1); }
            for (int y = 0; y < h; y++) { seed(0, y); seed(w - 1, y); }
            while (queue.Count > 0) {
                int k = queue.Dequeue(), x = k % w, y = k / w;
                if (x > 0) seed(x - 1, y);
                if (x < w - 1) seed(x + 1, y);
                if (y > 0) seed(x, y - 1);
                if (y < h - 1) seed(x, y + 1);
            }

            // 2. Alpha: background clear; edge pixels fade by how white they are, with the white unmixed.
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int k = y * w + x, i = y * stride + x * 4;
                if (bg[k]) { px[i + 3] = 0; continue; }
                bool edge = (x > 0 && bg[k - 1]) || (x < w - 1 && bg[k + 1]) || (y > 0 && bg[k - w]) || (y < h - 1 && bg[k + w]);
                if (!edge) { px[i + 3] = 255; continue; }
                int mn = Math.Min(px[i], Math.Min(px[i + 1], px[i + 2]));
                double a = Math.Max(0.15, Math.Min(1.0, (255 - mn) / 60.0));
                for (int c = 0; c < 3; c++) px[i + c] = (byte)Math.Max(0, Math.Min(255, (px[i + c] - (1 - a) * 255) / a));
                px[i + 3] = (byte)(a * 255);
            }
            Marshal.Copy(px, 0, data.Scan0, px.Length);
            bmp.UnlockBits(data);

            // 3. Objects: label coarse blocks that hold any foreground (8-connected).
            int bw = (w + Block - 1) / Block, bh = (h + Block - 1) / Block;
            var fill = new bool[bw * bh];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (!bg[y * w + x]) fill[(y / Block) * bw + x / Block] = true;
            var label = new int[bw * bh];
            var boxes = new List<int[]>(); // minX, minY, maxX, maxY (pixels), area (blocks)
            for (int s = 0; s < fill.Length; s++) {
                if (!fill[s] || label[s] != 0) continue;
                int id = boxes.Count + 1;
                var box = new[] { int.MaxValue, int.MaxValue, 0, 0, 0 };
                var q = new Queue<int>(); q.Enqueue(s); label[s] = id;
                while (q.Count > 0) {
                    int b = q.Dequeue(), bx = b % bw, by = b / bw;
                    box[0] = Math.Min(box[0], bx * Block); box[1] = Math.Min(box[1], by * Block);
                    box[2] = Math.Max(box[2], Math.Min(w, (bx + 1) * Block)); box[3] = Math.Max(box[3], Math.Min(h, (by + 1) * Block));
                    box[4]++;
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                        int nx = bx + dx, ny = by + dy;
                        if (nx < 0 || ny < 0 || nx >= bw || ny >= bh) continue;
                        int n = ny * bw + nx;
                        if (fill[n] && label[n] == 0) { label[n] = id; q.Enqueue(n); }
                    }
                }
                boxes.Add(box);
            }
            int biggest = 0;
            foreach (var b in boxes) biggest = Math.Max(biggest, b[4]);
            boxes.RemoveAll(b => b[4] < biggest * 0.04);
            if (boxes.Count != names.Length) return -boxes.Count;

            // 4. Reading order: rows by vertical overlap, then left to right.
            boxes.Sort((a, b) => a[1].CompareTo(b[1]));
            var ordered = new List<int[]>();
            int r0 = 0;
            while (r0 < boxes.Count) {
                int r1 = r0 + 1, rowBottom = boxes[r0][3];
                while (r1 < boxes.Count && boxes[r1][1] < rowBottom) { rowBottom = Math.Max(rowBottom, boxes[r1][3]); r1++; }
                var row = boxes.GetRange(r0, r1 - r0);
                row.Sort((a, b) => a[0].CompareTo(b[0]));
                ordered.AddRange(row);
                r0 = r1;
            }

            // 5. Crop each object to its box, trim, scale, save.
            for (int n = 0; n < names.Length; n++) {
                var b = ordered[n];
                var rect = Rectangle.FromLTRB(b[0], b[1], b[2], b[3]);
                using (var crop = bmp.Clone(rect, PixelFormat.Format32bppArgb)) {
                    Rectangle tight;
                    TightenToContent(crop, out tight);
                    using (var tightCrop = crop.Clone(tight, PixelFormat.Format32bppArgb)) {
                        double scale = Math.Min(1.0, (double)maxSide / Math.Max(tight.Width, tight.Height));
                        int ow = Math.Max(1, (int)Math.Round(tight.Width * scale)), oh = Math.Max(1, (int)Math.Round(tight.Height * scale));
                        using (var outBmp = new Bitmap(ow, oh, PixelFormat.Format32bppArgb))
                        using (var g = Graphics.FromImage(outBmp)) {
                            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
                            g.CompositingMode = CompositingMode.SourceCopy;
                            g.DrawImage(tightCrop, new Rectangle(0, 0, ow, oh));
                            outBmp.Save(System.IO.Path.Combine(outDir, "pic_" + names[n] + ".png"), ImageFormat.Png);
                        }
                    }
                }
            }
            return names.Length;
        }
    }

    // Trims fully transparent rows and columns left by the coarse blocks.
    static void TightenToContent(Bitmap b, out Rectangle r) {
        int minX = b.Width, minY = b.Height, maxX = -1, maxY = -1;
        var d = b.LockBits(new Rectangle(0, 0, b.Width, b.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        var p = new byte[d.Stride * b.Height];
        Marshal.Copy(d.Scan0, p, 0, p.Length);
        b.UnlockBits(d);
        for (int y = 0; y < b.Height; y++) for (int x = 0; x < b.Width; x++) {
            if (p[y * d.Stride + x * 4 + 3] > 20) { minX = Math.Min(minX, x); maxX = Math.Max(maxX, x); minY = Math.Min(minY, y); maxY = Math.Max(maxY, y); }
        }
        r = maxX < 0 ? new Rectangle(0, 0, b.Width, b.Height) : Rectangle.FromLTRB(minX, minY, maxX + 1, maxY + 1);
    }
}
'@

New-Item -ItemType Directory -Force $Out | Out-Null
foreach ($sheet in $sheets.Keys) {
    $src = Get-ChildItem $Raw -File | Where-Object { $_.BaseName -eq $sheet } | Select-Object -First 1
    if (-not $src) { Write-Warning "missing $sheet"; continue }
    $n = [Cutter]::Cut($src.FullName, [string[]]$sheets[$sheet], $Out, 600)
    if ($n -lt 0) { Write-Warning "$sheet : found $(-$n) objects, expected $($sheets[$sheet].Count)" } else { "$sheet -> $n" }
}
