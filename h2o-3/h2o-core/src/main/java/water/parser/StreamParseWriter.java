package water.parser;

import water.Futures;

public interface StreamParseWriter extends ParseWriter {
  StreamParseWriter nextChunk();
  StreamParseWriter reduce(StreamParseWriter dout);
  StreamParseWriter close();
  StreamParseWriter close(Futures fs);
}
