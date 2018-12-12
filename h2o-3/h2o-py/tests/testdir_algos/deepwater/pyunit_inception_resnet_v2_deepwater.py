from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deepwater import H2ODeepWaterEstimator

def Conv(data, num_filter, kernel=(1, 1), stride=(1, 1), pad=(0, 0), name=None, suffix='', withRelu=True, withBn=False):
    import mxnet as mx
    conv = mx.sym.Convolution(data=data, num_filter=num_filter, kernel=kernel, stride=stride, pad=pad,
                              name='%s%s_conv2d' % (name, suffix))
    if withBn:
        conv = mx.sym.BatchNorm(data=conv, name='%s%s_bn' % (name, suffix))
    if withRelu:
        conv = mx.sym.Activation(data=conv, act_type='relu', name='%s%s_relu' % (name, suffix))
    return conv


# Input Shape is 3*299*299 (th)
def InceptionResnetStem(data,
                        num_1_1, num_1_2, num_1_3,
                        num_2_1,
                        num_3_1, num_3_2,
                        num_4_1, num_4_2, num_4_3, num_4_4,
                        num_5_1,
                        name):
    import mxnet as mx
    stem_3x3 = Conv(data=data, num_filter=num_1_1, kernel=(3, 3), stride=(2, 2), name=('%s_conv' % name))
    stem_3x3 = Conv(data=stem_3x3, num_filter=num_1_2, kernel=(3, 3), name=('%s_stem' % name), suffix='_conv')
    stem_3x3 = Conv(data=stem_3x3, num_filter=num_1_3, kernel=(3, 3), pad=(1, 1), name=('%s_stem' % name),
                    suffix='_conv_1')

    pool1 = mx.sym.Pooling(data=stem_3x3, kernel=(3, 3), stride=(2, 2), pool_type='max',
                           name=('%s_%s_pool1' % ('max', name)))
    stem_1_3x3 = Conv(data=stem_3x3, num_filter=num_2_1, kernel=(3, 3), stride=(2, 2), name=('%s_stem_1' % name),
                      suffix='_conv_1')

    concat1 = mx.sym.Concat(*[pool1, stem_1_3x3], name=('%s_concat_1' % name))

    stem_1_1x1 = Conv(data=concat1, num_filter=num_3_1, name=('%s_stem_1' % name), suffix='_conv_2')
    stem_1_3x3 = Conv(data=stem_1_1x1, num_filter=num_3_2, kernel=(3, 3), name=('%s_stem_1' % name), suffix='_conv_3')

    stem_2_1x1 = Conv(data=concat1, num_filter=num_4_1, name=('%s_stem_2' % name), suffix='_conv_1')
    stem_2_7x1 = Conv(data=stem_2_1x1, num_filter=num_4_2, kernel=(7, 1), pad=(3, 0), name=('%s_stem_2' % name),
                      suffix='_conv_2')
    stem_2_1x7 = Conv(data=stem_2_7x1, num_filter=num_4_3, kernel=(1, 7), pad=(0, 3), name=('%s_stem_2' % name),
                      suffix='_conv_3')
    stem_2_3x3 = Conv(data=stem_2_1x7, num_filter=num_4_4, kernel=(3, 3), name=('%s_stem_2' % name), suffix='_conv_4')

    concat2 = mx.sym.Concat(*[stem_1_3x3, stem_2_3x3], name=('%s_concat_2' % name))

    pool2 = mx.sym.Pooling(data=concat2, kernel=(3, 3), stride=(2, 2), pool_type='max',
                           name=('%s_%s_pool2' % ('max', name)))
    stem_3_3x3 = Conv(data=concat2, num_filter=num_5_1, kernel=(3, 3), stride=(2, 2), name=('%s_stem_3' % name),
                      suffix='_conv_1', withRelu=False)

    concat3 = mx.sym.Concat(*[pool2, stem_3_3x3], name=('%s_concat_3' % name))
    bn1 = mx.sym.BatchNorm(data=concat3, name=('%s_bn1' % name))
    act1 = mx.sym.Activation(data=bn1, act_type='relu', name=('%s_relu1' % name))

    return act1


def InceptionResnetV2A(data,
                       num_1_1,
                       num_2_1, num_2_2,
                       num_3_1, num_3_2, num_3_3,
                       proj,
                       name,
                       scaleResidual=True):
    import mxnet as mx
    init = data

    a1 = Conv(data=data, num_filter=num_1_1, name=('%s_a_1' % name), suffix='_conv')

    a2 = Conv(data=data, num_filter=num_2_1, name=('%s_a_2' % name), suffix='_conv_1')
    a2 = Conv(data=a2, num_filter=num_2_2, kernel=(3, 3), pad=(1, 1), name=('%s_a_2' % name), suffix='_conv_2')

    a3 = Conv(data=data, num_filter=num_3_1, name=('%s_a_3' % name), suffix='_conv_1')
    a3 = Conv(data=a3, num_filter=num_3_2, kernel=(3, 3), pad=(1, 1), name=('%s_a_3' % name), suffix='_conv_2')
    a3 = Conv(data=a3, num_filter=num_3_3, kernel=(3, 3), pad=(1, 1), name=('%s_a_3' % name), suffix='_conv_3')

    merge = mx.sym.Concat(*[a1, a2, a3], name=('%s_a_concat1' % name))

    conv = Conv(data=merge, num_filter=proj, name=('%s_a_liner_conv' % name), withRelu=False)
    if scaleResidual:
        conv *= 0.1

    out = init + conv
    bn = mx.sym.BatchNorm(data=out, name=('%s_a_bn1' % name))
    act = mx.sym.Activation(data=bn, act_type='relu', name=('%s_a_relu1' % name))

    return act


def InceptionResnetV2B(data,
                       num_1_1,
                       num_2_1, num_2_2, num_2_3,
                       proj,
                       name,
                       scaleResidual=True):
    import mxnet as mx
    init = data

    b1 = Conv(data=data, num_filter=num_1_1, name=('%s_b_1' % name), suffix='_conv')

    b2 = Conv(data=data, num_filter=num_2_1, name=('%s_b_2' % name), suffix='_conv_1')
    b2 = Conv(data=b2, num_filter=num_2_2, kernel=(1, 7), pad=(0, 3), name=('%s_b_2' % name), suffix='_conv_2')
    b2 = Conv(data=b2, num_filter=num_2_3, kernel=(7, 1), pad=(3, 0), name=('%s_b_2' % name), suffix='_conv_3')

    merge = mx.sym.Concat(*[b1, b2], name=('%s_b_concat1' % name))

    conv = Conv(data=merge, num_filter=proj, name=('%s_b_liner_conv' % name), withRelu=False)
    if scaleResidual:
        conv *= 0.1

    out = init + conv
    bn = mx.sym.BatchNorm(data=out, name=('%s_b_bn1' % name))
    act = mx.sym.Activation(data=bn, act_type='relu', name=('%s_b_relu1' % name))

    return act


def InceptionResnetV2C(data,
                       num_1_1,
                       num_2_1, num_2_2, num_2_3,
                       proj,
                       name,
                       scaleResidual=True):
    import mxnet as mx
    init = data

    c1 = Conv(data=data, num_filter=num_1_1, name=('%s_c_1' % name), suffix='_conv')

    c2 = Conv(data=data, num_filter=num_2_1, name=('%s_c_2' % name), suffix='_conv_1')
    c2 = Conv(data=c2, num_filter=num_2_2, kernel=(1, 3), pad=(0, 1), name=('%s_c_2' % name), suffix='_conv_2')
    c2 = Conv(data=c2, num_filter=num_2_3, kernel=(3, 1), pad=(1, 0), name=('%s_c_2' % name), suffix='_conv_3')

    merge = mx.sym.Concat(*[c1, c2], name=('%s_c_concat1' % name))

    conv = Conv(data=merge, num_filter=proj, name=('%s_b_liner_conv' % name), withRelu=False)
    if scaleResidual:
        conv *= 0.1

    out = init + conv
    bn = mx.sym.BatchNorm(data=out, name=('%s_c_bn1' % name))
    act = mx.sym.Activation(data=bn, act_type='relu', name=('%s_c_relu1' % name))

    return act


def ReductionResnetV2A(data,
                       num_2_1,
                       num_3_1, num_3_2, num_3_3,
                       name):
    import mxnet as mx
    ra1 = mx.sym.Pooling(data=data, kernel=(3, 3), stride=(2, 2), pool_type='max', name=('%s_%s_pool1' % ('max', name)))

    ra2 = Conv(data=data, num_filter=num_2_1, kernel=(3, 3), stride=(2, 2), name=('%s_ra_2' % name), suffix='_conv', withRelu=False)

    ra3 = Conv(data=data, num_filter=num_3_1, name=('%s_ra_3' % name), suffix='_conv_1')
    ra3 = Conv(data=ra3, num_filter=num_3_2, kernel=(3, 3), pad=(1, 1), name=('%s_ra_3' % name), suffix='_conv_2')
    ra3 = Conv(data=ra3, num_filter=num_3_3, kernel=(3, 3), stride=(2, 2), name=('%s_ra_3' % name), suffix='_conv_3', withRelu=False)

    m = mx.sym.Concat(*[ra1, ra2, ra3], name=('%s_ra_concat1' % name))
    m = mx.sym.BatchNorm(data=m, name=('%s_ra_bn1' % name))
    m = mx.sym.Activation(data=m, act_type='relu', name=('%s_ra_relu1' % name))

    return m


def ReductionResnetV2B(data,
                       num_2_1, num_2_2,
                       num_3_1, num_3_2,
                       num_4_1, num_4_2, num_4_3,
                       name):
    import mxnet as mx
    rb1 = mx.sym.Pooling(data=data, kernel=(3, 3), stride=(2, 2), pool_type='max', name=('%s_%s_pool1' % ('max', name)))

    rb2 = Conv(data=data, num_filter=num_2_1, name=('%s_rb_2' % name), suffix='_conv_1')
    rb2 = Conv(data=rb2, num_filter=num_2_2, kernel=(3, 3), stride=(2, 2), name=('%s_rb_2' % name), suffix='_conv_2', withRelu=False)

    rb3 = Conv(data=data, num_filter=num_3_1, name=('%s_rb_3' % name), suffix='_conv_1')
    rb3 = Conv(data=rb3, num_filter=num_3_2, kernel=(3, 3), stride=(2, 2), name=('%s_rb_3' % name), suffix='_conv_2', withRelu=False)

    rb4 = Conv(data=data, num_filter=num_4_1, name=('%s_rb_4' % name), suffix='_conv_1')
    rb4 = Conv(data=rb4, num_filter=num_4_2, kernel=(3, 3), pad=(1, 1), name=('%s_rb_4' % name), suffix='_conv_2')
    rb4 = Conv(data=rb4, num_filter=num_4_3, kernel=(3, 3), stride=(2, 2), name=('%s_rb_4' % name), suffix='_conv_3', withRelu=False)

    m = mx.sym.Concat(*[rb1, rb2, rb3, rb4], name=('%s_rb_concat1' % name))
    m = mx.sym.BatchNorm(data=m, name=('%s_rb_bn1' % name))
    m = mx.sym.Activation(data=m, act_type='relu', name=('%s_rb_relu1' % name))

    return m


def circle_in3a(data,
                num_1_1,
                num_2_1, num_2_2,
                num_3_1, num_3_2, num_3_3,
                proj,
                name,
                scale,
                round):
    in3a = data
    for i in xrange(round):
        in3a = InceptionResnetV2A(in3a,
                                  num_1_1,
                                  num_2_1, num_2_2,
                                  num_3_1, num_3_2, num_3_3,
                                  proj,
                                  name + ('_%d' % i),
                                  scaleResidual=scale)
    return in3a


def circle_in2b(data,
                num_1_1,
                num_2_1, num_2_2, num_2_3,
                proj,
                name,
                scale,
                round):
    in2b = data
    for i in xrange(round):
        in2b = InceptionResnetV2B(in2b,
                                  num_1_1,
                                  num_2_1, num_2_2, num_2_3,
                                  proj,
                                  name + ('_%d' % i),
                                  scaleResidual=scale)
    return in2b


def circle_in2c(data,
                num_1_1,
                num_2_1, num_2_2, num_2_3,
                proj,
                name,
                scale,
                round):
    in2c = data
    for i in xrange(round):
        in2c = InceptionResnetV2C(in2c,
                                  num_1_1,
                                  num_2_1, num_2_2, num_2_3,
                                  proj,
                                  name + ('_%d' % i),
                                  scaleResidual=scale)
    return in2c


# create inception-resnet-v2
def get_symbol(num_classes=1000, scale=True):
    import mxnet as mx
    # input shape 3*229*229
    data = mx.symbol.Variable(name="data")

    # stage stem
    (num_1_1, num_1_2, num_1_3) = (32, 32, 64)
    num_2_1 = 96
    (num_3_1, num_3_2) = (64, 96)
    (num_4_1, num_4_2, num_4_3, num_4_4) = (64, 64, 64, 96)
    num_5_1 = 192

    in_stem = InceptionResnetStem(data,
                                  num_1_1, num_1_2, num_1_3,
                                  num_2_1,
                                  num_3_1, num_3_2,
                                  num_4_1, num_4_2, num_4_3, num_4_4,
                                  num_5_1,
                                  'stem_stage')

    # stage 5 x Inception Resnet A
    num_1_1 = 32
    (num_2_1, num_2_2) = (32, 32)
    (num_3_1, num_3_2, num_3_3) = (32, 48, 64)
    proj = 384

    in3a = circle_in3a(in_stem,
                       num_1_1,
                       num_2_1, num_2_2,
                       num_3_1, num_3_2, num_3_3,
                       proj,
                       'in3a',
                       scale,
                       5)

    # stage Reduction Resnet A
    num_1_1 = 384
    (num_2_1, num_2_2, num_2_3) = (256, 256, 384)

    re3a = ReductionResnetV2A(in3a,
                              num_1_1,
                              num_2_1, num_2_2, num_2_3,
                              're3a')

    # stage 10 x Inception Resnet B
    num_1_1 = 192
    (num_2_1, num_2_2, num_2_3) = (128, 160, 192)
    proj = 1152

    in2b = circle_in2b(re3a,
                       num_1_1,
                       num_2_1, num_2_2, num_2_3,
                       proj,
                       'in2b',
                       scale,
                       10)

    # stage ReductionB
    (num_1_1, num_1_2) = (256, 384)
    (num_2_1, num_2_2) = (256, 288)
    (num_3_1, num_3_2, num_3_3) = (256, 288, 320)

    re4b = ReductionResnetV2B(in2b,
                              num_1_1, num_1_2,
                              num_2_1, num_2_2,
                              num_3_1, num_3_2, num_3_3,
                              're4b')

    # stage 5 x Inception Resnet C
    num_1_1 = 192
    (num_2_1, num_2_2, num_2_3) = (192, 224, 256)
    proj = 2144

    in2c = circle_in2c(re4b,
                       num_1_1,
                       num_2_1, num_2_2, num_2_3,
                       proj,
                       'in2c',
                       scale,
                       5)

    # stage Average Poolingact1
    pool = mx.sym.Pooling(data=in2c, kernel=(8, 8), stride=(1, 1), pool_type="avg", name="global_pool")

    # stage Dropout
    dropout = mx.sym.Dropout(data=pool, p=0.2)
    # dropout =  mx.sym.Dropout(data=pool, p=0.8)
    flatten = mx.sym.Flatten(data=dropout, name="flatten")

    # output
    fc1 = mx.symbol.FullyConnected(data=flatten, num_hidden=num_classes, name='fc1')
    softmax = mx.symbol.SoftmaxOutput(data=fc1, name='softmax')
    return softmax



def deepwater_inception_resnet_v2():
  if not H2ODeepWaterEstimator.available(): return

  frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))
  print(frame.head(5))
  nclasses = frame[1].nlevels()[0]

  print("Creating the model architecture from scratch using the MXNet Python API")
  get_symbol(nclasses).save("/tmp/symbol_inception_resnet_v2-py.json")

  print("Importing the model architecture for training in H2O")
  model = H2ODeepWaterEstimator(epochs=50, #learning_rate=1e-3, learning_rate_annealing=1e-5,
                                mini_batch_size=16,
                                ## provide network specific information
                                network='user', network_definition_file="/tmp/symbol_inception_resnet_v2-py.json", image_shape=[299,299], channels=3)

  model.train(x=[0],y=1, training_frame=frame)
  model.show()
  error = model.model_performance(train=True).mean_per_class_error()
  assert error < 0.1, "mean classification error is too high : " + str(error) 

if __name__ == "__main__":
  pyunit_utils.standalone_test(deepwater_inception_resnet_v2)
else:
  deepwater_inception_resnet_v2()
