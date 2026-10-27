"""Convert XPS-style <Aps> pictogram XML into Android VectorDrawable XML.

usage: python aps2vector.py <input.xml> <output.xml>
"""
import sys
import xml.etree.ElementTree as ET


def fmt(v):
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def transform(matrix, x, y):
    a, b, c, d, e, f = matrix
    return a * x + c * y + e, b * x + d * y + f


def pt(el, matrix):
    return transform(matrix, float(el.get("X")), float(el.get("Y")))


def figure_to_path(fig, matrix):
    out = []
    cur = None

    def move_or_line(p):
        nonlocal cur
        if cur is None:
            out.append(f"M{fmt(p[0])},{fmt(p[1])}")
        elif abs(p[0] - cur[0]) > 1e-4 or abs(p[1] - cur[1]) > 1e-4:
            out.append(f"L{fmt(p[0])},{fmt(p[1])}")
        cur = p

    for seg in fig:
        if seg.tag == "PolyLine":
            for p in seg.findall("Point"):
                move_or_line(pt(p, matrix))
        elif seg.tag == "Bezier":
            move_or_line(pt(seg.find("StartPoint"), matrix))
            c1 = pt(seg.find("ControlPoint1"), matrix)
            c2 = pt(seg.find("ControlPoint2"), matrix)
            end = pt(seg.find("EndPoint"), matrix)
            out.append(
                f"C{fmt(c1[0])},{fmt(c1[1])} {fmt(c2[0])},{fmt(c2[1])} {fmt(end[0])},{fmt(end[1])}"
            )
            cur = end
        else:
            raise ValueError(f"unsupported segment {seg.tag}")
    if fig.get("IsClosed") == "True":
        out.append("Z")
    return "".join(out)


def argb_to_android(color):
    # XPS uses #AARRGGBB, which Android accepts as-is.
    return color.upper()


def walk(node, matrix_stack, paths):
    for child in node:
        if child.tag == "Clip":
            continue  # Full-page clip rectangles add nothing to a vector drawable.
        if child.tag == "Path":
            brush = child.find("Brush/SolidBrush")
            if brush is None:
                continue
            m = matrix_stack[-1]
            rt = child.get("RenderTransform")
            if rt:
                m = compose(m, [float(v) for v in rt.split()])
            data = "".join(figure_to_path(f, m) for f in child.findall("PathFigure"))
            fill_type = "evenOdd" if child.get("FillMode") == "EvenOdd" else "nonZero"
            paths.append((argb_to_android(brush.get("Color")), fill_type, data))
        else:
            rt = child.get("RenderTransform")
            if rt:
                matrix_stack.append(compose(matrix_stack[-1], [float(v) for v in rt.split()]))
                walk(child, matrix_stack, paths)
                matrix_stack.pop()
            else:
                walk(child, matrix_stack, paths)


def compose(outer, inner):
    """Returns the matrix applying `inner` first, then `outer`."""
    a1, b1, c1, d1, e1, f1 = outer
    a2, b2, c2, d2, e2, f2 = inner
    return [
        a1 * a2 + c1 * b2,
        b1 * a2 + d1 * b2,
        a1 * c2 + c1 * d2,
        b1 * c2 + d1 * d2,
        a1 * e2 + c1 * f2 + e1,
        b1 * e2 + d1 * f2 + f1,
    ]


def convert(src, dst):
    root = ET.parse(src).getroot()
    page = root.find(".//Page")
    width, height = page.get("Width"), page.get("Height")
    paths = []
    walk(page, [[1, 0, 0, 1, 0, 0]], paths)
    if not paths:
        raise ValueError(f"no filled paths in {src}")
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Generated from art/pictograms/*.aps.xml by aps2vector.py. Do not edit by hand. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        f'    android:viewportWidth="{width}"',
        f'    android:viewportHeight="{height}">',
    ]
    for color, fill_type, data in paths:
        lines += [
            "    <path",
            f'        android:fillColor="{color}"',
            f'        android:fillType="{fill_type}"',
            f'        android:pathData="{data}" />',
        ]
    lines.append("</vector>")
    with open(dst, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")
    print(f"{dst}: {len(paths)} paths")


if __name__ == "__main__":
    convert(sys.argv[1], sys.argv[2])
