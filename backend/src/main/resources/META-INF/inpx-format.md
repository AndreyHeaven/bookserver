# INPX format used by BookServerFull

This implementation reads `.inpx` as a ZIP archive containing one or more `.inp`
files encoded as UTF-8.

Each non-empty `.inp` line is pipe-separated (`|`) and uses this field order:

1. `AUTHOR` - authors separated by `:`, each author as `LastName,FirstName,MiddleName`
2. `GENRE` - genre codes separated by `:`
3. `TITLE`
4. `SERIES`
5. `SERNO`
6. `FILE` - ZIP archive name, with or without `.zip`
7. `SIZE`
8. `LIBID` - expected book file basename inside the ZIP archive
9. `DEL` - `1` means deleted and is skipped
10. `EXT` - book file extension, for example `fb2`
11. `DATE`
12. `LANG`
13. `LIBRATE`
14. `KEYWORDS`

For every record the importer searches configured ZIP archives for an entry
ending with `<LIBID>.<EXT>`. The physical file md5 is computed after extraction
and used for deduplication.
