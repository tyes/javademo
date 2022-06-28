package com.myorg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Template;

public class JavademoStackTest {

    private final static ObjectMapper JSON =
        new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);
    @Test
    public void testStack() throws IOException {
        App app = new App();
        JavademoStack stack = new JavademoStack(app, "test");

        Template template = Template.fromStack(stack);

        template.hasResourceProperties("AWS::EC2::VPC", new HashMap<String, String>() {{
            put("CidrBlock", "10.1.0.0/16");
        }});

      // synthesize the stack to a CloudFormation template and compare against
      // a checked-in JSON file.
      JsonNode actual = JSON.valueToTree(app.synth().getStackArtifact(stack.getArtifactId()).getTemplate());
      JsonNode expected = JSON.readTree(getClass().getResource("expected.json"));
      assertEquals(expected, actual);

    }
}
