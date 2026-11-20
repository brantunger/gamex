# The tests' own copy of the data files

`data/` here is a copy of `assets/data/` as it stood when the copy was made, and `levels/` a copy of the campaign's
stage files and the Training Range (`TestTuning.CAMPAIGN`, `TestTuning.TRAINING_RANGE`) for the tests that measure
or quote a stage's title, par or briefing. `TestTuning`
(`core/src/test/java/io/github/brantunger/gamex/TestTuning.java`) loads it, and the tests that build enemies, weapons,
simulations, menus and HUD text run on it. The numbers and the words those tests expect are the ones in these files,
so tuning a file of `assets/data/` changes no test.

- Do not copy a tuned value here. The copy is not the game's balance and does not have to follow it.
- A key added to a file of `assets/data/` needs its line here too, and in the file the test of its keys reads (its
  fixture in `../data-fixtures/`, or the lines written out in its test), in the change that adds the code reading
  it. A removed key is removed here as well.
- The menus of most tests are built with this text and font over the shipped campaign (`Levels.campaign()`), and
  they check that every stage title and briefing can be drawn. So a glyph a shipped stage file starts to use must
  be in `data/pixel-font.txt` here as well as in the shipped font.
- Changing a value here is a test change: the tests that expect the old value change with it.

The files the game ships with are tested on their own, whatever they say: each loads and passes its loader's checks,
`ShippedTextTest` measures the shipped text against the places it is drawn in, and the tests of a design promise
(`TestTuning.Balance`) are played on the shipped balance as well as on this copy.
