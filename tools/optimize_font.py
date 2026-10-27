"""Remove optional glyph names while preserving every Unicode mapping and drawing table.

Requires fonttools. Usage: python tools/optimize_font.py INPUT.ttf OUTPUT.ttf
"""
from pathlib import Path
import json
import sys
from fontTools.ttLib import TTFont


def optimize(source: Path, target: Path) -> dict:
    original = TTFont(source, recalcTimestamp=False)
    unicode_ids = {cp: original.getGlyphID(name) for cp, name in original.getBestCmap().items()}
    original_data = {tag: original.getTableData(tag) for tag in original.keys() if tag not in ('GlyphOrder', 'head', 'post')}
    original['post'].formatType = 3.0
    original['post'].extraNames = []
    original['post'].mapping = {}
    original.save(target, reorderTables=False)
    result = TTFont(target, recalcTimestamp=False, checkChecksums=2)
    assert unicode_ids == {cp: result.getGlyphID(name) for cp, name in result.getBestCmap().items()}
    assert len(original.getGlyphOrder()) == len(result.getGlyphOrder())
    for tag, data in original_data.items():
        assert data == result.getTableData(tag), f'Unexpected font table change: {tag}'
    return {'before_bytes': source.stat().st_size, 'after_bytes': target.stat().st_size,
            'unicode_mappings': len(unicode_ids), 'glyphs': len(result.getGlyphOrder()),
            'drawing_layout_and_metric_tables': 'byte-identical', 'change': 'post format 2 to format 3 (optional glyph names only)'}


if __name__ == '__main__':
    print(json.dumps(optimize(Path(sys.argv[1]), Path(sys.argv[2])), indent=2))
