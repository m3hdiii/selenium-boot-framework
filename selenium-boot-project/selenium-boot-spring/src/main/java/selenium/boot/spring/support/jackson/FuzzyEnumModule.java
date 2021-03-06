package selenium.boot.spring.support.jackson;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.google.common.base.CharMatcher;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;



/**
 * A module for de-serializing enums that is more permissive than the default.
 * <p>
 * This deserializer is more permissive in the following ways:
 * <ul>
 * <li>Whitespace is permitted but stripped from the input.</li>
 * <li>Dashes and periods in the value are converted to underscores.</li>
 * <li>Matching against the enum values is case insensitive.</li>
 * </ul>
 * source: io.dropwizard.jackson;
 *
 * @author <a href="mailto:solmarkn@gmail.com">Dani Vainstein</a>
 * @version %I%, %G%
 * @since 1.0
 */
public class FuzzyEnumModule extends Module
{
    //region Static definitions, members, initialization and constructors

    //---------------------------------------------------------------------
    // Static definitions, members, initialization and constructors
    //---------------------------------------------------------------------

    public FuzzyEnumModule()
    {
        super();
    }

    //endregion

    /**
     * Convert a string to an enum with more permissive rules than {@link Enum} valueOf().
     * <p/>
     * This method is more permissive in the following ways:
     * <ul>
     * <li>Whitespace is permitted but stripped from the input.</li>
     * <li>Dashes and periods in the value are converted to underscores.</li>
     * <li>Matching against the enum values is case insensitive.</li>
     * </ul>
     *
     * @param value     The string to convert.
     * @param constants The list of constants for the {@link Enum} to which you wish to convert.
     *
     * @return The enum or null, if no enum constant matched the input value.
     */
    @Nullable
    public static Enum<?> fromStringFuzzy( String value, Enum<?>[] constants )
    {
        final String text = CharMatcher.whitespace()
                                    .removeFrom( value )
                                    .replace( '-', '_' )
                                    .replace( '.', '_' );
        for( Enum<?> constant : constants )
        {
            if( constant.name().equalsIgnoreCase( text ) )
            {
                return constant;
            }
        }

        // In some cases there are certain enums that don't follow the same pattern across an enterprise.  So this
        // means that you have a mix of enums that use toString(), some use @JsonCreator, and some just use the
        // standard constant name().  This block handles finding the proper enum by toString()
        for( Enum<?> constant : constants )
        {
            if( constant.toString().equalsIgnoreCase( value ) )
            {
                return constant;
            }
        }

        return null;
    }

    @Override
    public String getModuleName()
    {
        return "permissive-enums";
    }

    @Override
    public Version version()
    {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule( final SetupContext context )
    {
        context.addDeserializers( new PermissiveEnumDeserializers() );
    }


    private static class PermissiveEnumDeserializer extends StdScalarDeserializer<Enum<?>>
    {
        private static final long serialVersionUID = 1L;

        private final Enum<?>[] constants;

        private final List<String> acceptedValues;

        @SuppressWarnings( "unchecked" )
        protected PermissiveEnumDeserializer( Class<Enum<?>> clazz )
        {
            super( clazz );
            this.constants = ( ( Class<Enum<?>> ) handledType() ).getEnumConstants();
            this.acceptedValues = new ArrayList<>();
            for( Enum<?> constant : constants )
            {
                acceptedValues.add( constant.name() );
            }
        }

        @Override
        public Enum<?> deserialize( JsonParser jp, DeserializationContext ctxt ) throws IOException
        {
            Enum<?> constant = fromStringFuzzy( jp.getText(), constants );
            if( constant != null )
            {
                return constant;
            }
            throw ctxt.weirdStringException( jp.getText(), handledType(), jp.getText() + " was not one of " + acceptedValues );
        }
    }


    private static class PermissiveEnumDeserializers extends Deserializers.Base
    {
        @Override
        @SuppressWarnings( "unchecked" )
        @Nullable
        public JsonDeserializer<?> findEnumDeserializer( Class<?> type,
                                                         DeserializationConfig config,
                                                         BeanDescription desc ) throws JsonMappingException
        {
            // If the user configured to use `toString` method to deserialize enums
            if( config.hasDeserializationFeatures(
                    DeserializationFeature.READ_ENUMS_USING_TO_STRING.getMask() ) )
            {
                return null;
            }

            // If there is a JsonCreator annotation we should use that instead of the PermissiveEnumDeserializer
            final Collection<AnnotatedMethod> factoryMethods = desc.getFactoryMethods();
            if( factoryMethods != null )
            {
                for( AnnotatedMethod am : factoryMethods )
                {
                    if( am.hasAnnotation( JsonCreator.class ) )
                    {
                        return null;
                    }
                }
            }

            // If any enum choice is annotated with an annotation from jackson, defer to
            // Jackson to do the deserialization
            for( Field field : type.getFields() )
            {
                for( Annotation annotation : field.getAnnotations() )
                {
                    final String packageName = annotation.annotationType().getPackage().getName();
                    if( packageName.equals( "com.fasterxml.jackson.annotation" ) )
                    {
                        return null;
                    }
                }
            }

            return new PermissiveEnumDeserializer( ( Class<Enum<?>> ) type );
        }
    }

}
