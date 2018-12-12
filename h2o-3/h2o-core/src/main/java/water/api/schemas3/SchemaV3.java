package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.api.Schema;

/**
 * Base Schema class for all v3 REST API objects.
 *
 * Any schema which is *NOT* used for requests (i.e. it cannot be an input to a REST API handler) should inherit from
 * this class.
 * However if a schema is used for requests, then it should inherit from {@link RequestSchemaV3}, which contains some
 * additional fields common for all REST API requests.
 */
public class SchemaV3<I extends Iced, S extends SchemaV3<I,S>> extends Schema<I, S> {

  @API(help="Metadata on this schema instance, to make it self-describing.", direction=API.Direction.OUTPUT)
  public Meta __meta;

  /**
   * Metadata for a Schema, including the version, name and type.  This information is included in all v3 REST API
   * responses as a field in the Schema so that the payloads are self-describing, and it is also available through
   * the /Metadata/schemas REST API endpoint for the purposes of REST service discovery.
   */
  public static final class Meta extends Iced {

    @API(help="Version number of this Schema.  Must not be changed after creation (treat as final).", direction=API.Direction.OUTPUT)
    public int schema_version;

    @API(help="Simple name of this Schema.  NOTE: the schema_names form a single namespace.", direction=API.Direction.OUTPUT)
    public String schema_name;

    @API(help="Simple name of H2O type that this Schema represents.  Must not be changed after creation (treat as final).", direction=API.Direction.OUTPUT)
    public String schema_type;

    /** Default constructor used only for newInstance() in generic reflection-based code. */
    public Meta() {}

    /** Standard constructor which supplies all the fields.  The fields should be treated as immutable once set. */
    public Meta(int version, String name, String type) {
      schema_version = version;
      schema_name = name;
      schema_type = type;
    }

    /** Used during markdown generation. */
    public String toString() { return schema_name; }
  }

  public SchemaV3() { this(null); }
  public SchemaV3(I impl) {
    __meta = new Meta(getSchemaVersion(), getSchemaName(), getSchemaType());
    if (impl != null)
      this.fillFromImpl(impl);
  }

  public water.AutoBuffer writeJSON(water.AutoBuffer ab) {
    // Ugly hack, but sometimes I find that __meta was not initialized by now; which means that constructor was
    // somehow skipped, which means the object was created in roundabout way and then unsafely cast... Hope we'll
    // find a proper solution to this issue eventually...
    if (__meta == null)
      __meta = new Meta(getSchemaVersion(), getSchemaName(), getSchemaType());
    return super.writeJSON(ab);
  }
}
