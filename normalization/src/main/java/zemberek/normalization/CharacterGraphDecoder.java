package zemberek.normalization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import zemberek.core.ScoredItem;
import zemberek.core.collections.FloatValueMap;
import zemberek.core.collections.IntMap;
import zemberek.core.turkish.TurkishAlphabet;

public class CharacterGraphDecoder {

  public static final Map<Character, String> TURKISH_FQ_NEAR_KEY_MAP = new HashMap<>();
  public static final Map<Character, String> TURKISH_Q_NEAR_KEY_MAP = new HashMap<>();
  static final float INSERTION_PENALTY = 1;
  static final float DELETION_PENALTY = 1;
  static final float SUBSTITUTION_PENALTY = 1;
  static final float NEAR_KEY_SUBSTITUTION_PENALTY = 0.5f;
  static final float TRANSPOSITION_PENALTY = 1;
  private static final Locale tr = new Locale("tr");
  public static final AsciiMatcher ASCII_TOLERANT_MATCHER = new AsciiMatcher();

  static {
    Map<Character, String> map = TURKISH_FQ_NEAR_KEY_MAP;
    map.put('a', "eüs");
    map.put('b', "svn");
    map.put('c', "vçx");
    map.put('ç', "czö");
    map.put('d', "orsf");
    map.put('e', "iawr");
    map.put('f', "gd");
    map.put('g', "fğh");
    map.put('ğ', "gıpü");
    map.put('h', "npgj");
    map.put('ı', "ğou");
    map.put('i', "ueş");
    map.put('j', "öhk");
    map.put('k', "tmjl");
    map.put('l', "mykş");
    map.put('m', "klnö");
    map.put('n', "rhbm");
    map.put('o', "ıdp");
    map.put('ö', "jvmç");
    map.put('p', "hqoğ");
    map.put('r', "dnet");
    map.put('s', "zbad");
    map.put('ş', "yli");
    map.put('t', "ükry");
    map.put('u', "iyı");
    map.put('ü', "atğ");
    map.put('v', "öcb");
    map.put('y', "lştu");
    map.put('z', "çsx");
    map.put('x', "wzc");
    map.put('q', "pqw");
    map.put('w', "qxe");
  }

  static {
    Map<Character, String> map = TURKISH_Q_NEAR_KEY_MAP;

    map.put('a', "s");
    map.put('b', "vn");
    map.put('c', "vx");
    map.put('ç', "ö");
    map.put('d', "sf");
    map.put('e', "wr");
    map.put('f', "gd");
    map.put('g', "fh");
    map.put('ğ', "pü");
    map.put('h', "gj");
    map.put('ı', "ou");
    map.put('i', "ş");
    map.put('j', "hk");
    map.put('k', "jl");
    map.put('l', "kş");
    map.put('m', "nö");
    map.put('n', "bm");
    map.put('o', "ıp");
    map.put('ö', "mç");
    map.put('p', "oğ");
    map.put('r', "et");
    map.put('s', "ad");
    map.put('ş', "li");
    map.put('t', "ry");
    map.put('u', "yı");
    map.put('ü', "ğ");
    map.put('v', "cb");
    map.put('y', "tu");
    map.put('z', "x");
    map.put('x', "zc");
    map.put('q', "w");
    map.put('w', "qe");
  }

  public final float maxPenalty;
  public final boolean checkNearKeySubstitution;
  public Map<Character, String> nearKeyMap = new HashMap<>();
  private CharacterGraph graph = new CharacterGraph();

  public CharacterGraphDecoder(float maxPenalty) {
    this.maxPenalty = maxPenalty;
    this.checkNearKeySubstitution = false;
  }

  public CharacterGraphDecoder() {
    this.maxPenalty = 1;
    this.checkNearKeySubstitution = false;
  }

  public CharacterGraphDecoder(CharacterGraph graph) {
    this.graph = graph;
    this.maxPenalty = 1;
    this.checkNearKeySubstitution = false;
  }

  public CharacterGraphDecoder(float maxPenalty, Map<Character, String> nearKeyMap) {
    this.maxPenalty = maxPenalty;
    this.nearKeyMap = Collections.unmodifiableMap(nearKeyMap);
    this.checkNearKeySubstitution = true;
  }

  public CharacterGraph getGraph() {
    return graph;
  }

  private String process(String str) {
    return str.toLowerCase(tr).replace("['.]", "");
  }

  public void addWord(String word) {
    graph.addWord(process(word), Node.TYPE_WORD);
  }

  public void addWords(String... words) {
    for (String word : words) {
      graph.addWord(process(word), Node.TYPE_WORD);
    }
  }

  public void buildDictionary(List<String> vocabulary) {
    for (String s : vocabulary) {
      graph.addWord(process(s), Node.TYPE_WORD);
    }
  }

  /**
   * Returns suggestions sorted by penalty.
   */
  public List<ScoredItem<String>> getSuggestionsWithScores(String input) {
    Decoder decoder = new Decoder();
    return getMatches(input, decoder);
  }

  private List<ScoredItem<String>> getMatches(String input, Decoder decoder) {
    FloatValueMap<String> results = decoder.decode(input);

    List<ScoredItem<String>> res = new ArrayList<>(results.size());
    for (String result : results) {
      res.add(new ScoredItem<>(result, results.get(result)));
    }
    res.sort((a, b) -> Float.compare(a.score, b.score));
    return res;
  }

  public List<ScoredItem<String>> getSuggestionsWithScores(String input, CharMatcher matcher) {
    Decoder decoder = new Decoder(matcher);
    return getMatches(input, decoder);
  }

  public FloatValueMap<String> decode(String input) {
    return new Decoder().decode(input);
  }

  public List<String> getSuggestions(String input) {
    return new Decoder().decode(input).getKeyList();
  }

  public List<String> getSuggestions(String input, CharMatcher matcher) {
    return new Decoder(matcher).decode(input).getKeyList();
  }

  public List<String> getSuggestionsSorted(String input) {
    List<ScoredItem<String>> s = getSuggestionsWithScores(input);
    List<String> result = new ArrayList<>(s.size());
    result.addAll(s.stream().map(s1 -> s1.item).collect(Collectors.toList()));
    return result;
  }

  enum Operation {
    NO_ERROR, INSERTION, DELETION, SUBSTITUTION, TRANSPOSITION, N_A
  }

  public interface CharMatcher {

    char[] matches(char c);
  }

  static class Hypothesis implements Comparable<Hypothesis> {

    Operation operation;
    int charIndex;
    Node node;
    float penalty;
    String word;
    String ending;
    Hypothesis previous;

    Hypothesis(Hypothesis previous, Node node, float penalty, Operation operation, String word,
        String ending) {
      this.previous = previous;
      this.node = node;
      this.penalty = penalty;
      this.charIndex = -1;
      this.operation = operation;
      this.word = word;
      this.ending = ending;
    }

    Hypothesis(Hypothesis previous, Node node, float penalty, int charIndex, Operation operation,
        String word, String ending) {
      this.previous = previous;
      this.node = node;
      this.penalty = penalty;
      this.charIndex = charIndex;
      this.operation = operation;
      this.word = word;
      this.ending = ending;
    }

    String backTrack() {
      StringBuilder sb = new StringBuilder();
      Hypothesis p = previous;
      while (p.node.chr != 0) {
        if (p.node != p.previous.node) {
          sb.append(p.node.chr);
        }
        p = p.previous;
      }
      return sb.reverse().toString();
    }

    String getContent() {
      String w = word == null ? "" : word;
      String e = ending == null ? "" : ending;
      return w + e;
    }

    void setWord(Node node) {
      if (node.word == null) {
        return;
      }
      if (node.getType() == Node.TYPE_WORD) {
        this.word = node.word;
      } else if (node.getType() == Node.TYPE_ENDING) {
        this.ending = node.word;
      }
    }

    Hypothesis getNew(Node node, float penaltyToAdd, Operation operation) {
      return new Hypothesis(this, node, this.penalty + penaltyToAdd, charIndex, operation,
          this.word, this.ending);
    }

    Hypothesis getNewMoveForward(Node node, float penaltyToAdd, Operation operation) {
      return new Hypothesis(this, node, this.penalty + penaltyToAdd, charIndex + 1, operation,
          this.word, this.ending);
    }

    Hypothesis getNew(Node node, float penaltyToAdd, int index, Operation operation) {
      return new Hypothesis(this, node, this.penalty + penaltyToAdd, index, operation, this.word,
          this.ending);
    }

    Hypothesis getNew(float penaltyToAdd, Operation operation) {
      return new Hypothesis(this, this.node, this.penalty + penaltyToAdd, charIndex, operation,
          this.word, this.ending);
    }

    @Override
    public int compareTo(Hypothesis o) {
      return Float.compare(penalty, o.penalty);
    }

    @Override
    public String toString() {
      return "Hypothesis{" +
          "previous=" + backTrack() + " " + previous.operation +
          ", node=" + node +
          ", penalty=" + penalty +
          ", index=" + charIndex +
          ", OP=" + operation.name() +
          '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Hypothesis that = (Hypothesis) o;

      if (charIndex != that.charIndex) {
        return false;
      }
      // TODO: this should not be here.
      if (Float.compare(that.penalty, penalty) != 0) {
        return false;
      }
      if (!node.equals(that.node)) {
        return false;
      }
      if (word != null ? !word.equals(that.word) : that.word != null) {
        return false;
      }
      return ending != null ? ending.equals(that.ending) : that.ending == null;
    }

    @Override
    public int hashCode() {
      int result = charIndex;
      result = 31 * result + node.hashCode();
      // TODO: this should not be here.
      result = 31 * result + (penalty != +0.0f ? Float.floatToIntBits(penalty) : 0);
      result = 31 * result + (word != null ? word.hashCode() : 0);
      result = 31 * result + (ending != null ? ending.hashCode() : 0);
      return result;
    }
  }

  private static class AsciiMatcher implements CharMatcher {

    static IntMap<char[]> map = new IntMap<>();

    public AsciiMatcher() {
      String allLetters = TurkishAlphabet.INSTANCE.getAllLetters() + "+.,'-";

      for (int i = 0; i < allLetters.length(); i++) {
        char[] ca = new char[1];
        char c = allLetters.charAt(i);
        ca[0] = c;
        map.put(c, ca);
      }
      // override some
      map.put('c', new char[]{'c', 'ç'});
      map.put('g', new char[]{'g', 'ğ'});
      map.put('ı', new char[]{'ı', 'i'});
      map.put('i', new char[]{'ı', 'i'});
      map.put('o', new char[]{'o', 'ö'});
      map.put('s', new char[]{'s', 'ş'});
      map.put('u', new char[]{'u', 'ü'});
      map.put('a', new char[]{'a', 'â'});
      map.put('i', new char[]{'i', 'î'});
      map.put('u', new char[]{'u', 'û'});
      map.put('C', new char[]{'C', 'Ç'});
      map.put('G', new char[]{'G', 'Ğ'});
      map.put('I', new char[]{'I', 'İ'});
      map.put('İ', new char[]{'İ', 'I'});
      map.put('O', new char[]{'O', 'Ö'});
      map.put('Ö', new char[]{'Ö', 'Ş'});
      map.put('U', new char[]{'U', 'Ü'});
      map.put('A', new char[]{'A', 'Â'});
      map.put('İ', new char[]{'İ', 'Î'});
      map.put('U', new char[]{'U', 'Û'});
    }

    @Override
    public char[] matches(char c) {
      char[] res = map.get(c);
      return res == null ? new char[]{c} : res;
    }
  }

  private class Decoder {

    FloatValueMap<String> finished = new FloatValueMap<>(8);
    CharMatcher matcher;

    Decoder() {
      this(null);
    }

    Decoder(CharMatcher matcher) {
      this.matcher = matcher;
    }

    FloatValueMap<String> decode(String input) {
      Hypothesis hyp = new Hypothesis(null, graph.getRoot(), 0, Operation.N_A, null, null);

      Set<Hypothesis> next = expand(hyp, input);
      while (true) {
        HashSet<Hypothesis> newHyps = new HashSet<>();
        for (Hypothesis hypothesis : next) {
          Set<Hypothesis> expand = expand(hypothesis, input);
          newHyps.addAll(expand);
        }
        if (newHyps.size() == 0) {
          break;
        }
        next = newHyps;
      }
      return finished;
    }

    private Set<Hypothesis> expand(Hypothesis hypothesis, String input) {

      Set<Hypothesis> newHypotheses = new HashSet<>();

      // get next character for this hypothesis.
      int nextIndex = hypothesis.charIndex + 1;
      char nextChar = nextIndex < input.length() ? input.charAt(nextIndex) : 0;

      // no-error. Hypothesis moves forward to the exact matching child nodes.
      if (nextIndex < input.length()) {

        // there can be more than one matching character, depending on the matcher.
        char[] cc = matcher == null ? null : matcher.matches(nextChar);
        // because there can be empty connections,
        // there can be more than 1 matching child nodes per character.
        if (hypothesis.node.hasEpsilonConnection()) {
          List<Node> childList = cc == null ?
              hypothesis.node.getChildList(nextChar) :
              hypothesis.node.getChildList(cc);
          for (Node child : childList) {
            Hypothesis h = hypothesis.getNewMoveForward(child, 0, Operation.NO_ERROR);
            h.setWord(child);
            newHypotheses.add(h);
            if (nextIndex >= input.length() - 1) {
              if (h.node.word != null) {
                addHypothesis(h);
              }
            }
          }
        } else {
          if (cc == null) {
            Node child = hypothesis.node.getImmediateChild(nextChar);
            if (child != null) {
              Hypothesis h = hypothesis.getNewMoveForward(child, 0, Operation.NO_ERROR);
              h.setWord(child);
              newHypotheses.add(h);
              if (nextIndex >= input.length() - 1) {
                if (h.node.word != null) {
                  addHypothesis(h);
                }
              }
            }
          } else {
            for (char c : cc) {
              Node child = hypothesis.node.getImmediateChild(c);
              if (child == null) {
                continue;
              }
              Hypothesis h = hypothesis.getNewMoveForward(child, 0, Operation.NO_ERROR);
              h.setWord(child);
              newHypotheses.add(h);
              if (nextIndex >= input.length() - 1) {
                if (h.node.word != null) {
                  addHypothesis(h);
                }
              }
            }
          }
        }
      } else if (hypothesis.node.word != null) {
        addHypothesis(hypothesis);
      }

      // we don't need to explore further if we reached to max penalty
      if (hypothesis.penalty >= maxPenalty) {
        return newHypotheses;
      }

      // For reducing List creation. IF there is no epsilon connection, retrieve the
      // internal data structure iterator.
      Iterable<Node> allChildNodes = hypothesis.node.hasEpsilonConnection() ?
          hypothesis.node.getAllChildNodes() : hypothesis.node.getImmediateChildNodeIterable();

      if (nextIndex < input.length()) {
        // substitution
        for (Node child : allChildNodes) {
          float penalty = 0;
          if (checkNearKeySubstitution) {
            if (child.chr != nextChar) {
              String nearCharactersString = nearKeyMap.get(child.chr);
              if (nearCharactersString != null && nearCharactersString.indexOf(nextChar) >= 0) {
                penalty = NEAR_KEY_SUBSTITUTION_PENALTY;
              } else {
                penalty = SUBSTITUTION_PENALTY;
              }
            }
          } else {
            penalty = SUBSTITUTION_PENALTY;
          }

          if (penalty > 0 && hypothesis.penalty + penalty <= maxPenalty) {
            Hypothesis h = hypothesis.getNewMoveForward(
                child,
                penalty,
                Operation.SUBSTITUTION);
            h.setWord(child);
            if (nextIndex == input.length() - 1) {
              if (h.node.word != null) {
                addHypothesis(h);
              }
            } else {
              newHypotheses.add(h);
            }
          }
        }
      }

      if (hypothesis.penalty + DELETION_PENALTY > maxPenalty) {
        return newHypotheses;
      }

      // deletion
      newHypotheses
          .add(hypothesis.getNewMoveForward(hypothesis.node, DELETION_PENALTY, Operation.DELETION));

      // insertion
      for (Node child : allChildNodes) {
        Hypothesis h = hypothesis.getNew(child, INSERTION_PENALTY, Operation.INSERTION);
        h.setWord(child);
        newHypotheses.add(h);
      }

      // transposition
      // TODO: make length check parametric. Also eliminate gross code duplication
      if (input.length() > 2 && nextIndex < input.length() - 1) {
        char transpose = input.charAt(nextIndex + 1);
        if (matcher != null) {
          char[] tt = matcher.matches(transpose);
          char[] cc = matcher.matches(nextChar);
          for (char t : tt) {
            List<Node> nextNodes = hypothesis.node.getChildList(t);
            for (Node nextNode : nextNodes) {
              for (char c : cc) {
                if (hypothesis.node.hasChild(t) && nextNode.hasChild(c)) {
                  for (Node n : nextNode.getChildList(c)) {
                    Hypothesis h = hypothesis.getNew(
                        n,
                        TRANSPOSITION_PENALTY,
                        nextIndex + 1,
                        Operation.TRANSPOSITION);
                    h.setWord(n);
                    if (nextIndex == input.length() - 1) {
                      if (h.node.word != null) {
                        addHypothesis(h);
                      }
                    } else {
                      newHypotheses.add(h);
                    }
                  }
                }
              }
            }
          }
        } else {
          List<Node> nextNodes = hypothesis.node.getChildList(transpose);
          for (Node nextNode : nextNodes) {
            if (hypothesis.node.hasChild(transpose) && nextNode.hasChild(nextChar)) {
              for (Node n : nextNode.getChildList(nextChar)) {
                Hypothesis h = hypothesis.getNew(
                    n,
                    TRANSPOSITION_PENALTY,
                    nextIndex + 1,
                    Operation.TRANSPOSITION);
                h.setWord(n);
                if (nextIndex == input.length() - 1) {
                  if (h.node.word != null) {
                    addHypothesis(h);
                  }
                } else {
                  newHypotheses.add(h);
                }
              }
            }
          }
        }
      }
      return newHypotheses;
    }

    private void addHypothesis(Hypothesis hypothesis) {
      String hypWord = hypothesis.getContent();
      if (!finished.contains(hypWord)) {
        finished.set(hypWord, hypothesis.penalty);
      } else if (finished.get(hypWord) > hypothesis.penalty) {
        finished.set(hypWord, hypothesis.penalty);
      }
    }
  }
}
