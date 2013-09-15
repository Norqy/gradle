/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.service

import org.gradle.api.Action
import org.gradle.internal.Factory
import spock.lang.Specification

import java.lang.reflect.Type

class DefaultServiceRegistryTest extends Specification {
    def TestRegistry registry = new TestRegistry()

    def throwsExceptionForUnknownService() {
        when:
        registry.get(StringBuilder.class)

        then:
        IllegalArgumentException e = thrown()
        e.message == "No service of type StringBuilder available in TestRegistry."
    }

    def delegatesToParentForUnknownService() {
        def value = BigDecimal.TEN
        def parent = Mock(ServiceRegistry)
        def registry = new TestRegistry(parent)

        when:
        def result = registry.get(BigDecimal)

        then:
        result == value

        and:
        1 * parent.get(BigDecimal) >> value
    }

    def delegatesToParentsForUnknownService() {
        def value = BigDecimal.TEN
        def parent1 = Mock(ServiceRegistry)
        def parent2 = Mock(ServiceRegistry)
        def registry = new DefaultServiceRegistry(parent1, parent2)

        when:
        def result = registry.get(BigDecimal)

        then:
        result == value

        and:
        1 * parent1.get(BigDecimal) >> { throw new UnknownServiceException(BigDecimal, "fail") }
        1 * parent2.get(BigDecimal) >> value
    }

    def throwsExceptionForUnknownParentService() {
        def parent = Mock(ServiceRegistry);
        def registry = new TestRegistry(parent)

        given:
        _ * parent.get(StringBuilder) >> { throw new UnknownServiceException(StringBuilder.class, "fail") }

        when:
        registry.get(StringBuilder)

        then:
        IllegalArgumentException e = thrown()
        e.message == "No service of type StringBuilder available in TestRegistry."
    }

    def returnsServiceInstanceThatHasBeenRegistered() {
        def value = BigDecimal.TEN
        def registry = new DefaultServiceRegistry()

        given:
        registry.add(BigDecimal, value)

        expect:
        registry.get(BigDecimal) == value
        registry.get(Number) == value
        registry.get(Object) == value
    }

    def usesFactoryMethodOnProviderToCreateServiceInstance() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new TestProvider())

        expect:
        registry.get(Integer) == 12
        registry.get(Number) == 12
    }

    def injectsServicesIntoProviderFactoryMethod() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new Object() {
            Integer createInteger() {
                return 12
            }

            String createString(Integer integer) {
                return integer.toString()
            }
        })

        expect:
        registry.get(String) == "12"
    }

    def injectsParentServicesIntoProviderFactoryMethod() {
        def parent = Mock(ServiceRegistry)
        def registry = new DefaultServiceRegistry(parent)
        registry.addProvider(new Object() {
            String createString(Number n) {
                return n.toString()
            }
        })

        when:
        def result = registry.get(String)

        then:
        result == '123'

        and:
        1 * parent.get(Number) >> 123
    }

    def injectsServiceRegistryIntoProviderFactoryMethod() {
        def parent = Mock(ServiceRegistry)
        def registry = new DefaultServiceRegistry(parent)
        registry.addProvider(new Object() {
            String createString(ServiceRegistry services) {
                assert services.is(registry)
                return services.get(Number).toString()
            }
        })
        registry.add(Integer, 123)

        expect:
        registry.get(String) == '123'
    }

    def failsWhenProviderFactoryMethodRequiresUnknownService() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new StringProvider())

        when:
        registry.get(String)

        then:
        ServiceLookupException e = thrown()
        e.message == "Cannot create service of type String using StringProvider.createString() as required service of type Runnable is not available."

        when:
        registry.get(Number)

        then:
        e = thrown()
        e.message == "Cannot create service of type String using StringProvider.createString() as required service of type Runnable is not available."
    }

    def failsWhenProviderFactoryMethodThrowsException() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new BrokenProvider())

        when:
        registry.get(String)

        then:
        ServiceLookupException e = thrown()
        e.message == "Could not create service of type String using BrokenProvider.createString()."
        e.cause == BrokenProvider.failure

        when:
        registry.get(Number)

        then:
        e = thrown()
        e.message == "Could not create service of type String using BrokenProvider.createString()."
        e.cause == BrokenProvider.failure
    }

    def cachesInstancesCreatedUsingAProviderFactoryMethod() {
        def registry = new DefaultServiceRegistry()
        def provider = new Object() {
            String createString(Number number) {
                return number.toString()
            }

            Integer createInteger() {
                return 12
            }
        }
        registry.addProvider(provider)

        expect:
        registry.get(Integer).is(registry.get(Integer))
        registry.get(Number).is(registry.get(Number))

        and:
        registry.get(String).is(registry.get(String))
    }

    def usesProviderDecoratorMethodToDecorateParentServiceInstance() {
        def parent = Mock(ServiceRegistry)
        def registry = new DefaultServiceRegistry(parent)
        registry.addProvider(new TestDecoratingProvider())

        given:
        _ * parent.get(Long) >> 110L

        expect:
        registry.get(Long) == 112L
        registry.get(Number) == 112L
        registry.get(Object) == 112L
    }

    def cachesServiceCreatedUsingProviderDecoratorMethod() {
        def parent = Mock(ServiceRegistry)
        def registry = new DefaultServiceRegistry(parent)
        registry.addProvider(new TestDecoratingProvider())

        given:
        _ * parent.get(Long) >> 11L

        expect:
        registry.get(Long).is(registry.get(Long))
    }

    def providerDecoratorMethodFailsWhenNoParentRegistry() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new TestDecoratingProvider())

        then:
        ServiceLookupException e = thrown()
        e.message == "Cannot use decorator methods when no parent registry is provided."
    }

    def failsWhenProviderDecoratorMethodRequiresUnknownService() {
        def parent = Stub(ServiceRegistry) {
            get(_) >> { throw new UnknownServiceException(it[0], "broken") }
        }
        def registry = new DefaultServiceRegistry(parent)

        given:
        registry.addProvider(new TestDecoratingProvider())

        when:
        registry.get(Long)

        then:
        ServiceLookupException e = thrown()
        e.message == "Cannot create service of type Long using TestDecoratingProvider.createLong() as required service of type Long is not available in parent registries."
    }

    def failsWhenProviderDecoratorMethodThrowsException() {
        def parent = Stub(ServiceRegistry) {
            get(Long) >> 12L
        }
        def registry = new DefaultServiceRegistry(parent)

        given:
        registry.addProvider(new BrokenDecoratingProvider())

        when:
        registry.get(Long)

        then:
        ServiceLookupException e = thrown()
        e.message == "Could not create service of type Long using BrokenDecoratingProvider.createLong()."
        e.cause == BrokenDecoratingProvider.failure
    }

    def failsWhenThereIsACycleInDependenciesForProviderFactoryMethods() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new ProviderWithCycle())

        when:
        registry.get(String)

        then:
        ServiceLookupException e = thrown()
        e.message == "Cannot create service of type Integer using ProviderWithCycle.createInteger() as there is a cycle in its dependencies."

        when:
        registry.getAll(Number)

        then:
        e = thrown()
        e.message == "Cannot create service of type Integer using ProviderWithCycle.createInteger() as there is a cycle in its dependencies."
    }

    def failsWhenAProviderFactoryMethodReturnsNull() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new NullProvider())

        when:
        registry.get(String)

        then:
        ServiceLookupException e = thrown()
        e.message == "Could not create service of type String using NullProvider.createString() as this method returned null."
    }

    def failsWhenAProviderDecoratorMethodReturnsNull() {
        def parent = Stub(ServiceRegistry) {
            get(String) >> "parent"
        }
        def registry = new DefaultServiceRegistry(parent)

        given:
        registry.addProvider(new NullDecorator())

        when:
        registry.get(String)

        then:
        ServiceLookupException e = thrown()
        e.message == "Could not create service of type String using NullDecorator.createString() as this method returned null."
    }

    def usesFactoryMethodToCreateServiceInstance() {
        expect:
        registry.get(String.class) == "12"
        registry.get(Integer.class) == 12
    }

    def cachesInstancesCreatedUsingAFactoryMethod() {
        expect:
        registry.get(Integer).is(registry.get(Integer))
        registry.get(Number).is(registry.get(Number))
    }

    def usesOverriddenFactoryMethodToCreateServiceInstance() {
        def registry = new TestRegistry() {
            @Override
            protected String createString() {
                return "overridden"
            }
        };

        expect:
        registry.get(String) == "overridden"
    }

    public void failsWhenMultipleServiceFactoriesCanCreateRequestedServiceType() {
        def registry = new RegistryWithAmbiguousFactoryMethods();

        expect:
        registry.get(String) == "hello"

        when:
        registry.get(Object)

        then:
        ServiceLookupException e = thrown()
        e.message == "Multiple services of type Object available in RegistryWithAmbiguousFactoryMethods."
    }

    def failsWhenArrayClassRequested() {
        when:
        registry.get(String[].class)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot locate service of array type String[]."
    }

    def usesDecoratorMethodToDecorateParentServiceInstance() {
        def parent = Mock(ServiceRegistry)
        def registry = new RegistryWithDecoratorMethods(parent)

        when:
        def result = registry.get(Long)

        then:
        result == 120L

        and:
        1 * parent.get(Long) >> 110L
    }

    def decoratorMethodFailsWhenNoParentRegistry() {
        when:
        new RegistryWithDecoratorMethods()

        then:
        ServiceLookupException e = thrown()
        e.message == "Cannot use decorator methods when no parent registry is provided."
    }

    def canRegisterServicesUsingAction() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.register({ ServiceRegistration registration ->
            registration.add(Number, 12)
            registration.addProvider(new Object() {
                String createString() {
                    return "hi"
                }
            })
        } as Action)

        expect:
        registry.get(Number) == 12
        registry.get(String) == "hi"
    }

    def canGetAllServicesOfAGivenType() {
        registry.addProvider(new Object(){
            String createOtherString() {
                return "hi"
            }
        })

        expect:
        registry.getAll(String) == ["12", "hi"]
        registry.getAll(Number) == [12]
    }

    def returnsEmptyCollectionWhenNoServicesOfGivenType() {
        expect:
        registry.getAll(Long).empty
    }

    def includesServicesFromParents() {
        def parent1 = Stub(ServiceRegistry)
        def parent2 = Stub(ServiceRegistry)
        def registry = new DefaultServiceRegistry(parent1, parent2)
        registry.addProvider(new Object() {
            Long createLong() {
                return 12;
            }
        });

        given:
        _ * parent1.getAll(Number) >> [123L]
        _ * parent2.getAll(Number) >> [456]

        expect:
        registry.getAll(Number) == [12, 123L, 456]
    }

    def canGetServiceAsFactoryWhenTheServiceImplementsFactoryInterface() {
        expect:
        registry.getFactory(BigDecimal) instanceof TestFactory
        registry.getFactory(Number) instanceof TestFactory
        registry.getFactory(BigDecimal).is(registry.getFactory(BigDecimal))
        registry.getFactory(Number).is(registry.getFactory(BigDecimal))
    }

    def canLocateFactoryWhenServiceInterfaceExtendsFactory() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.add(StringFactory, new StringFactory() {
            public String create() {
                return "value"
            }
        })

        expect:
        registry.getFactory(String.class).create() == "value"
    }

    def canGetAFactoryUsingParameterizedFactoryType() {
        def registry = new RegistryWithMultipleFactoryMethods()

        expect:
        def stringFactory = registry.get(stringFactoryType)
        stringFactory.create() == "hello"

        def numberFactory = registry.get(numberFactoryType)
        numberFactory.create() == 12
    }

    def canGetAFactoryUsingFactoryTypeWithBounds() throws NoSuchFieldException {
        expect:
        def superBigDecimalFactory = registry.get(superBigDecimalFactoryType)
        superBigDecimalFactory.create() == BigDecimal.valueOf(0)

        def extendsBigDecimalFactory = registry.get(extendsBigDecimalFactoryType)
        extendsBigDecimalFactory.create() == BigDecimal.valueOf(1)

        def extendsNumberFactory = registry.get(extendsNumberFactoryType)
        extendsNumberFactory.create() == BigDecimal.valueOf(2)
    }

    def cannotGetAFactoryUsingRawFactoryType() {
        when:
        registry.get(Factory)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot locate service of raw type Factory."
    }

    def usesAFactoryServiceToCreateInstances() {
        expect:
        registry.newInstance(BigDecimal) == BigDecimal.valueOf(0)
        registry.newInstance(BigDecimal) == BigDecimal.valueOf(1)
        registry.newInstance(BigDecimal) == BigDecimal.valueOf(2)
    }

    def throwsExceptionForUnknownFactory() {
        when:
        registry.getFactory(String)

        then:
        IllegalArgumentException e = thrown()
        e.message == "No factory for objects of type String available in TestRegistry."
    }

    def delegatesToParentForUnknownFactory() {
        def factory = Mock(Factory)
        def parent = Mock(ServiceRegistry)
        def registry = new TestRegistry(parent)

        when:
        def result = registry.getFactory(Map)

        then:
        result == factory

        and:
        1 * parent.getFactory(Map) >> factory
    }

    def usesDecoratorMethodToDecorateParentFactoryInstance() {
        def factory = Mock(Factory)
        def parent = Mock(ServiceRegistry)
        def registry = new RegistryWithDecoratorMethods(parent)

        given:
        _ * parent.getFactory(Long) >> factory
        _ * factory.create() >>> [10L, 20L]

        expect:
        registry.newInstance(Long) == 12L
        registry.newInstance(Long) == 22L
    }

    def failsWhenMultipleFactoriesAreAvailableForServiceType() {
        def registry = new RegistryWithAmbiguousFactoryMethods()

        when:
        registry.getFactory(Object)

        then:
        ServiceLookupException e = thrown()
        e.message == "Multiple factories for objects of type Object available in RegistryWithAmbiguousFactoryMethods."
    }

    def returnsServiceInstancesManagedByNestedServiceRegistry() {
        def nested = Mock(ServiceRegistry)
        def runnable = Mock(Runnable)

        given:
        registry.add(nested)

        when:
        def result = registry.get(Runnable)

        then:
        result == runnable

        and:
        1 * nested.get(Runnable) >> runnable
    }

    def throwsExceptionForUnknownServiceInNestedRegistry() {
        def nested = Mock(ServiceRegistry)

        given:
        registry.add(nested)
        _ * nested.get(Runnable) >> { throw new UnknownServiceException(Runnable, "fail") }

        when:
        registry.get(Runnable)

        then:
        IllegalArgumentException e = thrown()
        e.message == "No service of type Runnable available in TestRegistry."
    }

    def returnsServiceFactoriesManagedByNestedServiceRegistry() {
        def nested = Mock(ServiceRegistry)
        def factory = Mock(Factory)

        given:
        registry.add(nested)

        when:
        def result = registry.getFactory(Runnable)

        then:
        result == factory

        and:
        1 * nested.getFactory(Runnable) >> factory
    }

    def throwsExceptionForUnknownFactoryInNestedRegistry() {
        def nested = Mock(ServiceRegistry)

        given:
        registry.add(nested)
        _ * nested.getFactory(Runnable) >> { throw new UnknownServiceException(Runnable, "fail") }

        when:
        registry.getFactory(Runnable)

        then:
        IllegalArgumentException e = thrown()
        e.message == "No factory for objects of type Runnable available in TestRegistry."
    }

    def servicesCreatedByFactoryMethodsAreVisibleWhenUsingASubClass() {
        def registry = new TestRegistry() {
        }

        expect:
        registry.get(String) == "12"
        registry.get(Integer) == 12
    }

    def prefersServicesCreatedByFactoryMethodsOverNestedServices() {
        def parent = Mock(ServiceRegistry)
        def nested = Mock(ServiceRegistry)
        def registry = new TestRegistry(parent)

        given:
        registry.add(nested)

        expect:
        registry.get(String) == "12"
    }

    def prefersRegisteredServicesOverNestedServices() {
        def parent = Mock(ServiceRegistry)
        def nested = Mock(ServiceRegistry)
        def registry = new TestRegistry(parent)

        given:
        registry.add(nested)
        registry.add(BigDecimal, BigDecimal.ONE)

        expect:
        registry.get(BigDecimal.class) == BigDecimal.ONE
    }

    def prefersNestedServicesOverParentServices() {
        def parent = Mock(ServiceRegistry)
        def nested = Mock(ServiceRegistry)
        def registry = new TestRegistry(parent)
        def runnable = Mock(Runnable)

        given:
        registry.add(nested);

        when:
        def result = registry.get(Runnable)

        then:
        result == runnable

        and:
        1 * nested.get(Runnable) >> runnable
        0 * _._
    }

    def closeInvokesCloseMethodOnEachService() {
        def service = Mock(TestCloseService)

        given:
        registry.add(TestCloseService, service)

        when:
        registry.close()

        then:
        1 * service.close()
    }

    def closeInvokesStopMethodOnEachService() {
        def service = Mock(TestStopService)

        given:
        registry.add(TestStopService, service)

        when:
        registry.close()

        then:
        1 * service.stop()
    }

    def closeIgnoresServiceWithNoCloseOrStopMethod() {
        registry.add(String, "service")

        when:
        registry.close()

        then:
        noExceptionThrown()
    }

    def closeInvokesCloseMethodOnEachNestedServiceRegistry() {
        def nested = Mock(ClosableServiceRegistry)

        given:
        registry.add(nested)

        when:
        registry.close()

        then:
        1 * nested.close()
    }

    def closeInvokesCloseMethodOnEachServiceCreatedByProviderFactoryMethod() {
        def service = Mock(TestStopService)

        given:
        registry.addProvider(new Object() {
            TestStopService createServices() {
                return service
            }
        })
        registry.get(TestStopService)

        when:
        registry.close()

        then:
        1 * service.stop()
    }

    def doesNotStopServiceThatHasNotBeenCreated() {
        def service = Mock(TestStopService)

        given:
        registry.addProvider(new Object() {
            TestStopService createServices() {
                return service
            }
        })

        when:
        registry.close()

        then:
        0 * service.stop()
    }

    def discardsServicesOnClose() {
        given:
        registry.get(String)
        registry.close()

        when:
        registry.get(String)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot locate service of type String, as TestRegistry has been closed."
    }

    def discardsFactoriesOnClose() {
        given:
        registry.getFactory(BigDecimal)
        registry.close()

        when:
        registry.getFactory(BigDecimal)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot locate factory for objects of type BigDecimal, as TestRegistry has been closed."
    }

    private Factory<Number> numberFactory
    private Factory<String> stringFactory
    private Factory<? super BigDecimal> superBigDecimalFactory
    private Factory<? extends BigDecimal> extendsBigDecimalFactory
    private Factory<? extends Number> extendsNumberFactory

    private Type getNumberFactoryType() {
        return getClass().getDeclaredField("numberFactory").getGenericType();
    }

    private Type getStringFactoryType() {
        return getClass().getDeclaredField("stringFactory").getGenericType();
    }

    private Type getSuperBigDecimalFactoryType() {
        return getClass().getDeclaredField("superBigDecimalFactory").getGenericType()
    }

    private Type getExtendsBigDecimalFactoryType() {
        return getClass().getDeclaredField("extendsBigDecimalFactory").getGenericType()
    }

    private Type getExtendsNumberFactoryType() {
        return getClass().getDeclaredField("extendsNumberFactory").getGenericType()
    }

    private static class TestFactory implements Factory<BigDecimal> {
        int value;
        public BigDecimal create() {
            return BigDecimal.valueOf(value++)
        }
    }

    private interface StringFactory extends Factory<String> {
    }

    private static class TestRegistry extends DefaultServiceRegistry {
        public TestRegistry() {
        }

        public TestRegistry(ServiceRegistry parent) {
            super(parent)
        }

        protected String createString() {
            return get(Integer).toString()
        }

        protected Integer createInt() {
            return 12
        }

        protected Factory<BigDecimal> createTestFactory() {
            return new TestFactory()
        }
    }

    private static class TestProvider {
        String createString(Integer integer) {
            return integer.toString()
        }

        Integer createInt() {
            return 12
        }

        Factory<BigDecimal> createTestFactory() {
            return new TestFactory()
        }
    }

    private static class StringProvider {
        String createString(Runnable r) {
            return "hi"
        }

        Integer createInteger(String value) {
            return value.length()
        }
    }

    private static class ProviderWithCycle {
        String createString(Integer value) {
            return value.toString()
        }

        Integer createInteger(String value) {
            return value.length()
        }
    }

    private static class NullProvider {
        String createString() {
            return null
        }
    }

    private static class BrokenProvider {
        static def failure = new RuntimeException()

        String createString() {
            throw failure.fillInStackTrace()
        }

        Integer createInteger(String value) {
            return value.length()
        }
    }

    private static class TestDecoratingProvider {
        Long createLong(Long value) {
            return value + 2
        }
    }

    private static class BrokenDecoratingProvider {
        static def failure = new RuntimeException()

        Long createLong(Long value) {
            throw failure
        }
    }

    private static class NullDecorator {
        String createString(String value) {
            return null
        }
    }

    private static class RegistryWithAmbiguousFactoryMethods extends DefaultServiceRegistry {
        Object createObject() {
            return "hello"
        }

        String createString() {
            return "hello"
        }

        Factory<Object> createObjectFactory() {
            return new Factory<Object>() {
                public Object create() {
                    return createObject()
                }
            };
        }

        Factory<String> createStringFactory() {
            return new Factory<String>() {
                public String create() {
                    return createString()
                }
            };
        }
    }

    private static class RegistryWithDecoratorMethods extends DefaultServiceRegistry {
        public RegistryWithDecoratorMethods() {
        }

        public RegistryWithDecoratorMethods(ServiceRegistry parent) {
            super(parent)
        }

        protected Long createLong(Long value) {
            return value + 10
        }

        protected Factory<Long> createLongFactory(final Factory<Long> factory) {
            return new Factory<Long>() {
                public Long create() {
                    return factory.create() + 2
                }
            };
        }
    }

    private static class RegistryWithMultipleFactoryMethods extends DefaultServiceRegistry {
        Factory<Number> createObjectFactory() {
            return new Factory<Number>() {
                public Number create() {
                    return 12
                }
            };
        }

        Factory<String> createStringFactory() {
            return new Factory<String>() {
                public String create() {
                    return "hello"
                }
            };
        }
    }

    public interface TestCloseService {
        void close()
    }

    public interface TestStopService {
        void stop()
    }

    public interface ClosableServiceRegistry extends ServiceRegistry {
        void close()
    }
}